package com.vani.week4.backend.comment.service;

import com.github.f4b6a3.ulid.UlidCreator;
import com.vani.week4.backend.comment.dto.CommentCreateRequest;
import com.vani.week4.backend.comment.dto.CommentResponse;
import com.vani.week4.backend.comment.dto.CommentUpdateRequest;
import com.vani.week4.backend.comment.dto.CommentUpdateResponse;
import com.vani.week4.backend.comment.entity.Comment;
import com.vani.week4.backend.comment.repository.CommentRepository;
import com.vani.week4.backend.global.ErrorCode;
import com.vani.week4.backend.global.dto.SliceResponse;
import com.vani.week4.backend.global.exception.*;
import com.vani.week4.backend.infra.S3.S3Service;
import com.vani.week4.backend.post.entity.Post;
import com.vani.week4.backend.post.repository.PostRepository;
import com.vani.week4.backend.user.entity.User;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author vani
 * @since 10/15/25
 */
@Service
@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
public class CommentService {
    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final S3Service s3Service;

    /**
     * 댓글 조회 메서드,커서 기반 페이징
     * */
    public SliceResponse<CommentResponse> getComments(
            String postId,
            String cursorId,
            LocalDateTime cursorCreatedAt,
            int size ) {

        postRepository.findById(postId)
                .orElseThrow(() -> new PostNotFoundException(ErrorCode.RESOURCE_NOT_FOUND));

        //정렬된 최상위(최신 작성, id큰 순) 댓글들 가져오기
        Pageable pageable = PageRequest.of(0, size);
        Slice<Comment> topLevelComments = commentRepository.findTopLevelComments(postId, cursorCreatedAt, cursorId, pageable);

        // 슬라이스에서 리스트 꺼내고 리스트를 스트림으로 변환해서(함수형연산 가능하게)
        // toCommentWithReplies로 CommentResponse로 변환후 다시 리스트로 변환
        List<CommentResponse> responses = topLevelComments.getContent().stream()
                .map(this::toCommentWithReplies)
                .toList();

        // 다음 커서 생성, 더보기 버튼
        SliceResponse.Cursor nextCursor = null;
        if (topLevelComments.hasNext() && !responses.isEmpty()) {
            Comment lastComment = topLevelComments.getContent()
                    .get(topLevelComments.getContent().size() - 1);
            nextCursor = new SliceResponse.Cursor(
                    lastComment.getId(),
                    lastComment.getCreatedAt()
            );
        }

        return new SliceResponse<CommentResponse>(responses, nextCursor, topLevelComments.hasNext());
    }

    /**comment 형태의 댓글을 CommentResponse의 형태로 변환하는 메서드,
     * CommentGroup이용해서 같은 그룹의 댓글들을 불러오고,
     * 트리구조 형성과 대댓글 리스트, hasmore, replucount를 알맞게 넣어서 반환
     * @param comment : 쿼리를 통해 가져온 댓글
     * @return : 같은 그룹내의 트리구조가 형성된 CommentResponse 반환
     */
    private CommentResponse toCommentWithReplies(Comment comment){
        //같은 그룹의 댓글들 리스트(최상위 루트댓글 제외)
        List<Comment> replies =
                commentRepository.findRepliesByCommentGroup(comment.getCommentGroup());
        //리스트를 순회해서 트리구조의 CommentResponse형태의 리스트(여기서 최상위 = 실제 대댓글)
        List<CommentResponse> replyResponses = buildReplyTree(replies);

        return new CommentResponse(
                comment.getId(),
                comment.getParentId(),
                comment.getContent(),
                comment.getCommentGroup(),
                comment.getCreatedAt(),
                comment.getDepth(),
                toAuthor(comment.getUser()),
                replyResponses,
                false,
                replies.size()
        );
    }

    //dto 변환 헬퍼메서드
    private CommentResponse.Author toAuthor(User user) {

        String profileImageKey = user.getProfileImageKey();
        String authorProfileUrl = null;

        if (profileImageKey != null && !profileImageKey.isBlank()) {
            authorProfileUrl = s3Service.createPresignedGetUrl(profileImageKey);
        }

        return new CommentResponse.Author(
                user.getNickname(),
                authorProfileUrl
        );
    }

    /**댓글들을 순회하면서 댓글의 트리구조를 만들어주는 메서드
     *
     * @param replies : CommentGroup이 동일한 댓글들 리스트(루트제외)
     * @return firstLevelReplies : 트리구조를 가진 최상위 대댓글 리스트
     */
    private List<CommentResponse> buildReplyTree(List<Comment> replies){
        Map<String, CommentResponse> commentMap = new HashMap<>();
        List<CommentResponse> firstLevelReplies = new ArrayList<>();

        //가져온 댓글들을 CommentResponse로 변환
        for (Comment reply : replies){
            CommentResponse response = toCommentWithReplies(reply);
            commentMap.put(reply.getId(), response);
        }

        //트리구조 형성
        for (Comment reply : replies){
            CommentResponse response = commentMap.get(reply.getId());
            if (reply.getDepth() == 1) {
                //최상위 대댓글
                firstLevelReplies.add(response);
            } else if (reply.getDepth() > 1) {
                //2이상의 깊이는 부모의 replies에 추가
                //replies : CommentResponse의 필드
                String parentId = reply.getParentId();
                CommentResponse parent = commentMap.get(parentId);
                if (parent != null) {
                    parent.replies().add(response);
                }
            }
        }

        return firstLevelReplies;
    }

    /**
     * 댓글 생성 메서드
     */
    @Transactional
    public CommentResponse createComment(String postId, User user, CommentCreateRequest request){

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new PostNotFoundException(ErrorCode.RESOURCE_NOT_FOUND));

        String commentId = UlidCreator.getUlid().toString();

        String safeContent = HtmlUtils.htmlEscape(request.content());

        // 대댓글인지 검증
        Comment parent = null;
        if (request.parentId().isPresent()){
            parent = commentRepository.findById(request.parentId().get())
                    .orElseThrow(() -> new CommentNotFoundException(ErrorCode.RESOURCE_NOT_FOUND));

            //악의적인 오류 유발 댓글 방지
            if(!parent.getId().equals(post.getId())){
                throw new InvalidCommentException(ErrorCode.INVALID_INPUT);
            }

            //댓글 깊이 제한
            //TODO 깊이 제한을 어디까지 할것인지 확인 필요
            if(parent.getDepth() >= 3) {
                throw new MaxDepthExceededException(ErrorCode.INVALID_INPUT);
            }
        }

        Comment comment = Comment.builder()
                .id(commentId)
                .user(user)
                .post(post)
                .parentId(request.parentId().orElse(null))
                .depth(parent == null ? 0 : parent.getDepth() + 1)
                .commentGroup(parent == null ? commentId : parent.getCommentGroup())
                .content(safeContent)
                .build();

        commentRepository.save(comment);
        //TODO Count로직 개선하기
        post.incrementCommentCount();

        return toCommentResponse(comment);
    }

    private CommentResponse toCommentResponse(Comment comment){
        return new CommentResponse(
                comment.getId(),
                comment.getParentId(),
                comment.getContent(),
                comment.getCommentGroup(),
                comment.getCreatedAt(),
                comment.getDepth(),
                toAuthor(comment.getUser()),
                new ArrayList<>(),  // 대댓글 리스트 (생성 시에는 빈 배열)
                false,              // 더 불러올 대댓글 없음
                0                   // 대댓글 개수 0
        );
    }

    /**
     * 댓글 수정 메서드
     * */
    @Transactional
    public CommentUpdateResponse updateComment(String postId, String commentId,User user, CommentUpdateRequest request){

        postRepository.findById(postId)
                .orElseThrow(() -> new PostNotFoundException(ErrorCode.RESOURCE_NOT_FOUND));

        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new CommentNotFoundException(ErrorCode.RESOURCE_NOT_FOUND));

        if (!comment.getUser().getId().equals(user.getId())) {
            throw new UnauthorizedException(ErrorCode.UNAUTHORIZED);
        }

        comment.updateContent(HtmlUtils.htmlEscape(request.content()));

        commentRepository.save(comment);

        return new CommentUpdateResponse(
                comment.getId(),
                comment.getContent(),
                comment.getUpdatedAt()
        );
    }

    /**
     * 댓글 삭제 메서드
     * TODO (Soft delete), enduser에게 삭제됨 표시
     * */
    @Transactional
    public void deleteComment(String postId, String commentId, User user){

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new PostNotFoundException(ErrorCode.RESOURCE_NOT_FOUND));

        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new CommentNotFoundException(ErrorCode.RESOURCE_NOT_FOUND));

        if (!comment.getUser().getId().equals(user.getId())) {
            throw new UnauthorizedException(ErrorCode.UNAUTHORIZED);
        }
        commentRepository.delete(comment);
        post.decreaseCommentCount();
    }

}
