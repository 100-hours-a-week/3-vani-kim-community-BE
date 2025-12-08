package com.vani.week4.backend.post.dto.response;

import java.time.LocalDateTime;

/**
 * @author vani
 * @since 10/14/25
 */
public record PostSummaryResponse (
    String postId,
    String title,
    LocalDateTime createdAt,
    Author author,
    Stats stats
) {
    public record Author (
        String nickname,
        String authorProfileUrl
    ) {}
    public record Stats(
            long likeCount,
            long commentCount,
            long viewCount
    ) {}
}
