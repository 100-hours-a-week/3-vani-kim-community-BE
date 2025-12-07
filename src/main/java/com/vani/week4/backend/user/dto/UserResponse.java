package com.vani.week4.backend.user.dto;


/**
 * @author vani
 * @since 10/14/25
 */
public record UserResponse(
    String nickname,
    String email,
    String presignedProfileImageUrl
) {}

