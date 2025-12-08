package com.vani.week4.backend.comment.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @author vani
 * @since 10/15/25
 */
public record CommentResponse(
        String commentId,
        String parentId,
        String content,
        String commentGroup,
        LocalDateTime createdAt,
        Integer depth,
        Author author,
        //대댓글 리스트
        List<CommentResponse> replies,
        //더 있는지
        boolean hasMore,
        //대댓글 수
        int replyCount

) {
    public record Author (
            String nickname,
            String authorProfileImageUrl
    ) {}
}
