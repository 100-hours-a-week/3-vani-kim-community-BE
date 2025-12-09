package com.vani.week4.backend.loadtest;

import com.github.f4b6a3.ulid.UlidCreator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 부하 테스트용 대량 데이터 생성 서비스
 * - User: 10,000명
 * - Post: 1,000,000개
 * - PostContent: 1,000,000개
 * - Comment: 10,000,000개 (게시글당 평균 10개)
 * - Like: 30,000,000개 (게시글당 평균 30개)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoadTestDataGenerator {

    private final JdbcTemplate jdbcTemplate;

    // 설정값 (로컬 테스트용으로 작은 값으로 변경됨)
    private static final int USER_COUNT = 100;        // 기본: 10_000
    private static final int POST_COUNT = 1_000;      // 기본: 1_000_000
    private static final int AVG_COMMENTS_PER_POST = 1;   // 기본: 10
    private static final int AVG_LIKES_PER_POST = 3;     // 기본: 30

    private static final int BATCH_SIZE = 10;        // 기본: 1000

    // 테스트 데이터 식별을 위한 접두사 (닉네임 10자 제한 고려)
    private static final String TEST_DATA_PREFIX = "lt";

    // 데이터 생성 시작일 (과거 1년 전부터)
    private static final LocalDateTime START_DATE = LocalDateTime.now().minus(365, ChronoUnit.DAYS);

    /**
     * 모든 테스트 데이터 생성
     */
    public void generateAllData() {
        log.info("===== 부하 테스트 데이터 생성 시작 =====");
        long startTime = System.currentTimeMillis();

        try {
            // 1. 회원 데이터 생성
            List<String> userIds = generateUsers();
            log.info("✓ 회원 {} 명 생성 완료", userIds.size());

            // 2. 게시글 및 게시글 내용 생성
            List<String> postIds = generatePosts(userIds);
            log.info("✓ 게시글 {} 개 생성 완료", postIds.size());

            // 3. 댓글 생성
            long commentCount = generateComments(userIds, postIds);
            log.info("✓ 댓글 {} 개 생성 완료", commentCount);

            // 4. 좋아요 생성
            long likeCount = generateLikes(userIds, postIds);
            log.info("✓ 좋아요 {} 개 생성 완료", likeCount);

            long endTime = System.currentTimeMillis();
            log.info("===== 데이터 생성 완료 =====");
            log.info("총 소요 시간: {} 초", (endTime - startTime) / 1000);

        } catch (Exception e) {
            log.error("데이터 생성 중 오류 발생", e);
            throw new RuntimeException("데이터 생성 실패", e);
        }
    }

    /**
     * 1. 회원 데이터 생성 (10,000명)
     */
    @Transactional
    public List<String> generateUsers() {
        log.info("회원 데이터 생성 시작... (목표: {} 명)", USER_COUNT);

        List<String> userIds = new ArrayList<>();
        String userSql = "INSERT INTO users (id, nickname, profile_image_key, user_status, user_role, created_at) VALUES (?, ?, ?, ?, ?, ?)";
        String authSql = "INSERT INTO user_auths (id, user_id, email, provider, password_hash) VALUES (?, ?, ?, ?, ?)";

        for (int batch = 0; batch < USER_COUNT / BATCH_SIZE; batch++) {
            final int startIdx = batch * BATCH_SIZE;
            final List<String> batchUserIds = new ArrayList<>();

            for (int i = 0; i < BATCH_SIZE; i++) {
                batchUserIds.add(UlidCreator.getUlid().toString());
            }

            // 회원 정보 배치 삽입
            jdbcTemplate.batchUpdate(userSql, new BatchPreparedStatementSetter() {
                @Override
                public void setValues(PreparedStatement ps, int i) throws SQLException {
                    String userId = batchUserIds.get(i);
                    ps.setString(1, userId);
                    ps.setString(2, TEST_DATA_PREFIX + "user" + (startIdx + i));  // 테스트 데이터 식별용 접두사
                    ps.setString(3, null); // profileImageKey
                    ps.setString(4, "ACTIVE");
                    ps.setString(5, "USER");
                    ps.setTimestamp(6, Timestamp.valueOf(randomDateTime(START_DATE, LocalDateTime.now())));
                }

                @Override
                public int getBatchSize() {
                    return batchUserIds.size();
                }
            });

            // 인증 정보 배치 삽입
            jdbcTemplate.batchUpdate(authSql, new BatchPreparedStatementSetter() {
                @Override
                public void setValues(PreparedStatement ps, int i) throws SQLException {
                    String userId = batchUserIds.get(i);
                    ps.setString(1, UlidCreator.getUlid().toString());
                    ps.setString(2, userId);
                    ps.setString(3, "user" + (startIdx + i) + "@test.com");
                    ps.setInt(4, 0); // ProviderType.LOCAL의 ordinal 값 (0)
                    ps.setString(5, "$2a$10$dummyPasswordHashForLoadTest"); // BCrypt 형식의 더미 해시
                }

                @Override
                public int getBatchSize() {
                    return batchUserIds.size();
                }
            });

            userIds.addAll(batchUserIds);

            if ((batch + 1) % 10 == 0) {
                log.info("  진행률: {} / {} ({} %)", (batch + 1) * BATCH_SIZE, USER_COUNT,
                    ((batch + 1) * BATCH_SIZE * 100 / USER_COUNT));
            }
        }

        return userIds;
    }

    /**
     * 2. 게시글 및 게시글 내용 생성 (1,000,000개)
     */
    @Transactional
    public List<String> generatePosts(List<String> userIds) {
        log.info("게시글 데이터 생성 시작... (목표: {} 개)", POST_COUNT);

        List<String> postIds = new ArrayList<>();
        String postSql = "INSERT INTO posts (id, user_id, title, view_count, comment_count, like_count, post_status, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        String contentSql = "INSERT INTO post_contents (post_id, content, post_image_key) VALUES (?, ?, ?)";

        Random random = new Random();
        AtomicInteger counter = new AtomicInteger(0);

        for (int batch = 0; batch < POST_COUNT / BATCH_SIZE; batch++) {
            final int startIdx = batch * BATCH_SIZE;
            final List<String> batchPostIds = new ArrayList<>();

            for (int i = 0; i < BATCH_SIZE; i++) {
                batchPostIds.add(UlidCreator.getUlid().toString());
            }

            // 게시글 배치 삽입
            jdbcTemplate.batchUpdate(postSql, new BatchPreparedStatementSetter() {
                @Override
                public void setValues(PreparedStatement ps, int i) throws SQLException {
                    String postId = batchPostIds.get(i);
                    String userId = userIds.get(random.nextInt(userIds.size()));

                    ps.setString(1, postId);
                    ps.setString(2, userId);
                    ps.setString(3, "부하테스트 게시글 제목 " + (startIdx + i));
                    ps.setInt(4, random.nextInt(1000)); // viewCount
                    ps.setInt(5, 0); // commentCount (나중에 업데이트)
                    ps.setInt(6, 0); // likeCount (나중에 업데이트)
                    ps.setString(7, "ACTIVE");
                    ps.setTimestamp(8, Timestamp.valueOf(randomDateTime(START_DATE, LocalDateTime.now())));
                }

                @Override
                public int getBatchSize() {
                    return batchPostIds.size();
                }
            });

            // 게시글 내용 배치 삽입
            jdbcTemplate.batchUpdate(contentSql, new BatchPreparedStatementSetter() {
                @Override
                public void setValues(PreparedStatement ps, int i) throws SQLException {
                    String postId = batchPostIds.get(i);
                    ps.setString(1, postId);
                    ps.setString(2, generatePostContent(startIdx + i));
                    ps.setString(3, null); // postImageKey
                }

                @Override
                public int getBatchSize() {
                    return batchPostIds.size();
                }
            });

            postIds.addAll(batchPostIds);

            if ((batch + 1) % 100 == 0) {
                log.info("  진행률: {} / {} ({} %)", (batch + 1) * BATCH_SIZE, POST_COUNT,
                    ((batch + 1) * BATCH_SIZE * 100 / POST_COUNT));
            }
        }

        return postIds;
    }

    /**
     * 3. 댓글 생성 (10,000,000개, 게시글당 평균 10개)
     */
    @Transactional
    public long generateComments(List<String> userIds, List<String> postIds) {
        long totalComments = (long) POST_COUNT * AVG_COMMENTS_PER_POST;
        log.info("댓글 데이터 생성 시작... (목표: 약 {} 개)", totalComments);

        String sql = "INSERT INTO comment (id, user_id, post_id, parent_id, depth, comment_group, content, comment_status, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

        String updateSql = "UPDATE post SET comment_count = comment_count + ? WHERE id = ?";

        Map<String, Integer> postCommentDelta =  new HashMap<>();

        Random random = new Random();
        AtomicInteger counter = new AtomicInteger(0);

        // 각 게시글마다 랜덤하게 댓글 생성 (평균 10개)
        for (int postBatch = 0; postBatch < postIds.size(); postBatch += BATCH_SIZE) {
            int endIdx = Math.min(postBatch + BATCH_SIZE, postIds.size());
            List<CommentData> batchComments = new ArrayList<>();

            // 배치 내 각 게시글에 댓글 생성
            for (int i = postBatch; i < endIdx; i++) {
                String postId = postIds.get(i);
                // 포아송 분포로 댓글 개수 결정 (평균 10개)
                int commentCount = Math.min(random.nextInt(20) + 1, 30); // 1~30개

                // postId별 댓글 수를 기록
                postCommentDelta.merge(postId, commentCount, Integer::sum);

                for (int j = 0; j < commentCount; j++) {
                    String commentId = UlidCreator.getUlid().toString();
                    String userId = userIds.get(random.nextInt(userIds.size()));

                    batchComments.add(new CommentData(
                        commentId,
                        userId,
                        postId,
                        null, // parentId (대부분 루트 댓글)
                        0, // depth
                        commentId, // commentGroup (루트 댓글이므로 자신의 ID)
                        "부하테스트 댓글 내용 " + counter.incrementAndGet(),
                        randomDateTime(START_DATE, LocalDateTime.now())
                    ));
                }
            }

            // 댓글 배치 삽입
            if (!batchComments.isEmpty()) {
                jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement ps, int i) throws SQLException {
                        CommentData comment = batchComments.get(i);
                        ps.setString(1, comment.id);
                        ps.setString(2, comment.userId);
                        ps.setString(3, comment.postId);
                        ps.setString(4, comment.parentId);
                        ps.setInt(5, comment.depth);
                        ps.setString(6, comment.commentGroup);
                        ps.setString(7, comment.content);
                        ps.setString(8, "ACTIVE");
                        ps.setTimestamp(9, Timestamp.valueOf(comment.createdAt));
                    }

                    @Override
                    public int getBatchSize() {
                        return batchComments.size();
                    }
                });
            }

            if ((postBatch / BATCH_SIZE + 1) % 100 == 0) {
                log.info("  진행률: {} / {} ({} %), 생성된 댓글: {}",
                    endIdx, postIds.size(), (endIdx * 100 / postIds.size()), counter.get());
            }

            List<Map.Entry<String, Integer>> entries = new ArrayList<>(postCommentDelta.entrySet());

            for(int i = 0; i < entries.size(); i+= BATCH_SIZE) {
                int end = Math.min(i + BATCH_SIZE, entries.size());
                List<Map.Entry<String, Integer>> batch = entries.subList(i, end);

                jdbcTemplate.batchUpdate(updateSql, new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement ps, int idx) throws SQLException {
                        Map.Entry<String, Integer> entry = entries.get(idx);
                        ps.setString(1, entry.getKey());
                        ps.setString(2, entry.getKey());
                    }
                    @Override
                    public int getBatchSize() {
                        return batch.size();
                    }
                });
            }
        }

        return counter.get();
    }

    /**
     * 4. 좋아요 생성 (30,000,000개, 게시글당 평균 30개)
     */
    @Transactional
    public long generateLikes(List<String> userIds, List<String> postIds) {
        long totalLikes = (long) POST_COUNT * AVG_LIKES_PER_POST;
        log.info("좋아요 데이터 생성 시작... (목표: 약 {} 개)", totalLikes);

        String sql = "INSERT IGNORE INTO user_post_like (user_id, post_id) VALUES (?, ?)";
        Random random = new Random();
        AtomicInteger counter = new AtomicInteger(0);

        // 각 게시글마다 랜덤하게 좋아요 생성 (평균 30개)
        for (int postBatch = 0; postBatch < postIds.size(); postBatch += BATCH_SIZE) {
            int endIdx = Math.min(postBatch + BATCH_SIZE, postIds.size());
            List<LikeData> batchLikes = new ArrayList<>();

            // 배치 내 각 게시글에 좋아요 생성
            for (int i = postBatch; i < endIdx; i++) {
                String postId = postIds.get(i);
                // 랜덤하게 좋아요 개수 결정 (평균 30개)
                int likeCount = random.nextInt(60) + 1; // 1~60개

                // 중복 방지를 위해 Set 사용
                Set<String> selectedUsers = new HashSet<>();
                while (selectedUsers.size() < likeCount && selectedUsers.size() < userIds.size()) {
                    selectedUsers.add(userIds.get(random.nextInt(userIds.size())));
                }

                for (String userId : selectedUsers) {
                    batchLikes.add(new LikeData(userId, postId));
                    counter.incrementAndGet();
                }
            }

            // 좋아요 배치 삽입
            if (!batchLikes.isEmpty()) {
                jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement ps, int i) throws SQLException {
                        LikeData like = batchLikes.get(i);
                        ps.setString(1, like.userId);
                        ps.setString(2, like.postId);
                    }

                    @Override
                    public int getBatchSize() {
                        return batchLikes.size();
                    }
                });
            }

            if ((postBatch / BATCH_SIZE + 1) % 100 == 0) {
                log.info("  진행률: {} / {} ({} %), 생성된 좋아요: {}",
                    endIdx, postIds.size(), (endIdx * 100 / postIds.size()), counter.get());
            }
        }

        return counter.get();
    }

    /**
     * 테스트 데이터만 선택적으로 삭제 (기존 데이터는 보존)
     * 'lt'로 시작하는 닉네임을 가진 회원과 관련된 모든 데이터 삭제
     */
    @Transactional
    public void deleteAllTestData() {
        log.info("===== 테스트 데이터 삭제 시작 =====");
        log.info("기존 데이터는 보존하고 'lt'로 시작하는 테스트 데이터만 삭제합니다.");

        // 1. 테스트 회원 ID 조회
        String selectTestUsersSql = "SELECT id FROM users WHERE nickname LIKE ?";
        List<String> testUserIds = jdbcTemplate.queryForList(selectTestUsersSql, String.class, TEST_DATA_PREFIX + "%");

        if (testUserIds.isEmpty()) {
            log.info("삭제할 테스트 데이터가 없습니다.");
            return;
        }

        log.info("테스트 회원 {} 명 발견", testUserIds.size());

        // 2. 테스트 회원이 작성한 게시글 ID 조회
        String selectTestPostsSql = "SELECT id FROM posts WHERE user_id IN (" +
            String.join(",", testUserIds.stream().map(id -> "?").toList()) + ")";
        List<String> testPostIds = jdbcTemplate.queryForList(selectTestPostsSql, String.class, testUserIds.toArray());

        log.info("테스트 게시글 {} 개 발견", testPostIds.size());

        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0");

        // 3. 좋아요 삭제 (테스트 회원이 누른 좋아요 + 테스트 게시글의 좋아요)
        if (!testUserIds.isEmpty()) {
            String deleteLikesByUserSql = "DELETE FROM user_post_like WHERE user_id IN (" +
                String.join(",", testUserIds.stream().map(id -> "?").toList()) + ")";
            int deletedLikes1 = jdbcTemplate.update(deleteLikesByUserSql, testUserIds.toArray());
            log.info("  테스트 회원의 좋아요 {} 개 삭제", deletedLikes1);
        }

        if (!testPostIds.isEmpty()) {
            String deleteLikesByPostSql = "DELETE FROM user_post_like WHERE post_id IN (" +
                String.join(",", testPostIds.stream().map(id -> "?").toList()) + ")";
            int deletedLikes2 = jdbcTemplate.update(deleteLikesByPostSql, testPostIds.toArray());
            log.info("  테스트 게시글의 좋아요 {} 개 삭제", deletedLikes2);
        }

        // 4. 댓글 삭제 (테스트 게시글의 댓글)
        if (!testPostIds.isEmpty()) {
            String deleteCommentsSql = "DELETE FROM comment WHERE post_id IN (" +
                String.join(",", testPostIds.stream().map(id -> "?").toList()) + ")";
            int deletedComments = jdbcTemplate.update(deleteCommentsSql, testPostIds.toArray());
            log.info("  테스트 댓글 {} 개 삭제", deletedComments);
        }

        // 5. 게시글 내용 삭제
        if (!testPostIds.isEmpty()) {
            String deletePostContentsSql = "DELETE FROM post_contents WHERE post_id IN (" +
                String.join(",", testPostIds.stream().map(id -> "?").toList()) + ")";
            int deletedContents = jdbcTemplate.update(deletePostContentsSql, testPostIds.toArray());
            log.info("  테스트 게시글 내용 {} 개 삭제", deletedContents);
        }

        // 6. 게시글 삭제
        if (!testPostIds.isEmpty()) {
            String deletePostsSql = "DELETE FROM posts WHERE id IN (" +
                String.join(",", testPostIds.stream().map(id -> "?").toList()) + ")";
            int deletedPosts = jdbcTemplate.update(deletePostsSql, testPostIds.toArray());
            log.info("  테스트 게시글 {} 개 삭제", deletedPosts);
        }

        // 7. 인증 정보 삭제
        if (!testUserIds.isEmpty()) {
            String deleteAuthsSql = "DELETE FROM user_auths WHERE user_id IN (" +
                String.join(",", testUserIds.stream().map(id -> "?").toList()) + ")";
            int deletedAuths = jdbcTemplate.update(deleteAuthsSql, testUserIds.toArray());
            log.info("  테스트 인증 정보 {} 개 삭제", deletedAuths);
        }

        // 8. 회원 삭제
        if (!testUserIds.isEmpty()) {
            String deleteUsersSql = "DELETE FROM users WHERE id IN (" +
                String.join(",", testUserIds.stream().map(id -> "?").toList()) + ")";
            int deletedUsers = jdbcTemplate.update(deleteUsersSql, testUserIds.toArray());
            log.info("  테스트 회원 {} 명 삭제", deletedUsers);
        }

        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1");

        log.info("===== 테스트 데이터 삭제 완료 =====");
        log.info("기존 데이터는 보존되었습니다.");
    }

    /**
     * 모든 데이터를 강제로 삭제 (TRUNCATE 사용)
     * 경고: 기존 데이터도 모두 삭제됩니다!
     */
    @Transactional
    public void truncateAllTables() {
        log.warn("===== 모든 테이블 데이터 삭제 시작 (TRUNCATE) =====");
        log.warn("경고: 기존 데이터도 모두 삭제됩니다!");

        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0");

        jdbcTemplate.execute("TRUNCATE TABLE user_post_like");
        log.info("✓ 좋아요 테이블 초기화");

        jdbcTemplate.execute("TRUNCATE TABLE comment");
        log.info("✓ 댓글 테이블 초기화");

        jdbcTemplate.execute("TRUNCATE TABLE post_contents");
        log.info("✓ 게시글 내용 테이블 초기화");

        jdbcTemplate.execute("TRUNCATE TABLE posts");
        log.info("✓ 게시글 테이블 초기화");

        jdbcTemplate.execute("TRUNCATE TABLE user_auths");
        log.info("✓ 인증 테이블 초기화");

        jdbcTemplate.execute("TRUNCATE TABLE users");
        log.info("✓ 회원 테이블 초기화");

        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1");

        log.warn("===== 모든 테이블 데이터 삭제 완료 =====");
    }

    // ===== 유틸리티 메서드 =====

    private LocalDateTime randomDateTime(LocalDateTime start, LocalDateTime end) {
        long startEpoch = start.toEpochSecond(java.time.ZoneOffset.UTC);
        long endEpoch = end.toEpochSecond(java.time.ZoneOffset.UTC);
        long randomEpoch = startEpoch + (long) (Math.random() * (endEpoch - startEpoch));
        return LocalDateTime.ofEpochSecond(randomEpoch, 0, java.time.ZoneOffset.UTC);
    }

    private String generatePostContent(int index) {
        // content 컬럼 길이 제한을 고려해 간단한 내용으로 생성
        return String.format("부하테스트 게시글 #%d 내용입니다.", index);
    }

    // ===== 내부 데이터 클래스 =====

    private static class CommentData {
        String id;
        String userId;
        String postId;
        String parentId;
        int depth;
        String commentGroup;
        String content;
        LocalDateTime createdAt;

        CommentData(String id, String userId, String postId, String parentId,
                   int depth, String commentGroup, String content, LocalDateTime createdAt) {
            this.id = id;
            this.userId = userId;
            this.postId = postId;
            this.parentId = parentId;
            this.depth = depth;
            this.commentGroup = commentGroup;
            this.content = content;
            this.createdAt = createdAt;
        }
    }

    private static class LikeData {
        String userId;
        String postId;

        LikeData(String userId, String postId) {
            this.userId = userId;
            this.postId = postId;
        }
    }
}