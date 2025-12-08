package com.vani.week4.backend.post;

import com.github.f4b6a3.ulid.UlidCreator;
import com.vani.week4.backend.infra.S3.S3Service;
import com.vani.week4.backend.interaction.repository.LikeRepository;
import com.vani.week4.backend.interaction.service.LikeService;
import com.vani.week4.backend.post.dto.request.PostCreateRequest;
import com.vani.week4.backend.post.dto.response.PostDetailResponse;
import com.vani.week4.backend.post.entity.Post;
import com.vani.week4.backend.post.repository.PostRepository;
import com.vani.week4.backend.post.service.PostService;
import com.vani.week4.backend.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 게시글 생성 서비스 테스트
 * @author vani
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class PostServiceTest {

    @Mock
    private PostRepository postRepository;

    @Mock
    private LikeRepository likeRepository;

    @Mock
    private LikeService likeService;

    @Mock
    private S3Service s3Service;

    @InjectMocks
    private PostService postService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.createUser(
                UlidCreator.getUlid().toString(),
                "테스트유저",
                "profile-image-key"
        );
    }

    @Test
    @DisplayName("게시글 생성 성공 - 이미지 없이")
    void createPost_Success_WithoutImage() {
        // given
        PostCreateRequest request = new PostCreateRequest(
                "테스트 게시글 제목",
                "테스트 게시글 내용입니다.",
                null
        );

        String expectedAuthorImageUrl = "https://example.com/presigned/profile-image-key";

        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(likeService.getLikeCount(anyString())).thenReturn(0);
        // 작성자 프로필 presigned URL 목킹
        when(s3Service.createPresignedGetUrl("profile-image-key"))
                .thenReturn(expectedAuthorImageUrl);

        // when
        PostDetailResponse response = postService.createPost(testUser, request);

        // then
        assertThat(response).isNotNull();
        assertThat(response.postId()).isNotNull();
        assertThat(response.title()).isEqualTo("테스트 게시글 제목");
        assertThat(response.contentDetail().content()).contains("테스트 게시글 내용입니다.");

        // 게시글 자체 이미지 URL은 없어야 함
        assertThat(response.contentDetail().postImageUrl()).isNull();

        // 작성자 정보 검증
        assertThat(response.author().nickname()).isEqualTo("테스트유저");
        assertThat(response.author().authorProfileUrl())
                .isEqualTo(expectedAuthorImageUrl);

        assertThat(response.stats().likeCount()).isEqualTo(0);
        assertThat(response.stats().commentCount()).isEqualTo(0);
        assertThat(response.stats().viewCount()).isEqualTo(0);
        assertThat(response.stats().isLiked()).isFalse();

        verify(postRepository, times(1)).save(any(Post.class));

        // S3 presigned URL은 "작성자 프로필" 1번만 호출되는 게 맞음
        verify(s3Service, times(1)).createPresignedGetUrl("profile-image-key");
    }

    @Test
    @DisplayName("게시글 생성 성공 - 이미지 포함")
    void createPost_Success_WithImage() {
        // given
        PostCreateRequest request = new PostCreateRequest(
                "이미지 포함 게시글",
                "이미지가 있는 게시글 내용",
                "test-image-key-12345"
        );

        String expectedImageUrl = "https://example.com/presigned-url/test-image-key-12345";

        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(s3Service.createPresignedGetUrl("test-image-key-12345")).thenReturn(expectedImageUrl);
        when(likeService.getLikeCount(anyString())).thenReturn(0);

        // when
        PostDetailResponse response = postService.createPost(testUser, request);

        // then
        assertThat(response).isNotNull();
        assertThat(response.postId()).isNotNull();
        assertThat(response.title()).isEqualTo("이미지 포함 게시글");
        assertThat(response.contentDetail().content()).contains("이미지가 있는 게시글 내용");
        assertThat(response.contentDetail().postImageUrl()).isEqualTo(expectedImageUrl);
        assertThat(response.author().nickname()).isEqualTo("테스트유저");

        verify(postRepository, times(1)).save(any(Post.class));
        verify(s3Service, times(1)).createPresignedGetUrl("test-image-key-12345");
    }

    @Test
    @DisplayName("게시글 생성 시 내용이 HTML 이스케이프 처리됨")
    void createPost_ContentIsHtmlEscaped() {
        // given
        PostCreateRequest request = new PostCreateRequest(
                "XSS 테스트 제목",
                "<script>alert('XSS')</script>일반 내용",
                null
        );

        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(likeService.getLikeCount(anyString())).thenReturn(0);

        // when
        PostDetailResponse response = postService.createPost(testUser, request);

        // then
        assertThat(response.contentDetail().content()).doesNotContain("<script>");
        assertThat(response.contentDetail().content()).contains("&lt;script&gt;");
        assertThat(response.contentDetail().content()).contains("일반 내용");

        verify(postRepository, times(1)).save(any(Post.class));
    }

    @Test
    @DisplayName("게시글 생성 시 초기 통계값이 0으로 설정됨")
    void createPost_InitialStatsAreZero() {
        // given
        PostCreateRequest request = new PostCreateRequest(
                "통계 테스트",
                "통계 초기값 확인",
                null
        );

        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(likeService.getLikeCount(anyString())).thenReturn(0);

        // when
        PostDetailResponse response = postService.createPost(testUser, request);

        // then
        assertThat(response.stats().viewCount()).isEqualTo(0);
        assertThat(response.stats().commentCount()).isEqualTo(0);
        assertThat(response.stats().likeCount()).isEqualTo(0);
        assertThat(response.stats().isLiked()).isFalse();
    }
}