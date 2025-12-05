package com.vani.week4.backend.post;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.f4b6a3.ulid.UlidCreator;
import com.vani.week4.backend.post.dto.request.PostCreateRequest;
import com.vani.week4.backend.post.entity.Post;
import com.vani.week4.backend.post.repository.PostRepository;
import com.vani.week4.backend.user.entity.User;
import com.vani.week4.backend.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 게시글 생성 통합 테스트
 * - 실제 DB (H2) 사용
 * - Controller → Service → Repository 전체 플로우 테스트
 *
 * @author vani
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)  // Security 필터 비활성화
@ActiveProfiles("test")
@Transactional
class PostIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private UserRepository userRepository;

    private User testUser;

    @BeforeEach
    void setUp() {
        // 실제 DB에 테스트 사용자 저장
        testUser = User.createUser(
                UlidCreator.getUlid().toString(),
                "통합테스트유저",
                "profile-image-key"
        );
        userRepository.save(testUser);
    }

    @AfterEach
    void tearDown() {
        // 테스트 데이터 정리 (@Transactional로 자동 롤백됨)
        postRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("게시글 생성 통합 테스트 - 전체 플로우")
    void createPost_IntegrationTest() throws Exception {
        // given
        PostCreateRequest request = new PostCreateRequest(
                "통합테스트 게시글",
                "실제 DB에 저장되는 게시글입니다.",
                null
        );

        String requestBody = objectMapper.writeValueAsString(request);

        // when & then
        mockMvc.perform(post("/api/v1/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody)
                        // @CurrentUser가 요구하는 authenticatedUserId attribute 설정
                        .requestAttr("authenticatedUserId", testUser.getId())
                )
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("통합테스트 게시글"))
                .andExpect(jsonPath("$.contentDetail.content").exists())
                .andExpect(jsonPath("$.author.nickname").value("통합테스트유저"));

        // DB에 실제로 저장되었는지 확인
        long postCount = postRepository.count();
        assertThat(postCount).isEqualTo(1);

        Post savedPost = postRepository.findAll().get(0);
        assertThat(savedPost.getTitle()).isEqualTo("통합테스트 게시글");
        assertThat(savedPost.getPostContent().getContent()).contains("실제 DB에 저장되는 게시글입니다.");
    }

    @Test
    @DisplayName("게시글 생성 성공 - 이미지 포함")
    void createPost_WithImage() throws Exception {
        // given
        PostCreateRequest request = new PostCreateRequest(
                "이미지 포함 게시글",
                "이미지가 있는 게시글 내용",
                "test-image-key-12345"
        );

        String requestBody = objectMapper.writeValueAsString(request);

        // when & then
        mockMvc.perform(post("/api/v1/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody)
                        .requestAttr("authenticatedUserId", testUser.getId())
                )
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("이미지 포함 게시글"))
                .andExpect(jsonPath("$.contentDetail.content").exists())
                .andExpect(jsonPath("$.contentDetail.postImageUrl").exists());

        // DB에 실제로 저장되었는지 확인
        long postCount = postRepository.count();
        assertThat(postCount).isEqualTo(1);
    }
}