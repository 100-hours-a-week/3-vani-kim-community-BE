package com.vani.week4.backend.user.service;

import com.vani.week4.backend.auth.dto.request.SignUpRequest;
import com.vani.week4.backend.auth.service.AuthService;
import com.vani.week4.backend.infra.S3.S3Service;
import com.vani.week4.backend.user.dto.WithdrawRequest;
import com.vani.week4.backend.auth.entity.Auth;
import com.vani.week4.backend.auth.entity.ProviderType;
import com.vani.week4.backend.global.ErrorCode;
import com.vani.week4.backend.global.exception.AuthNotFoundException;
import com.vani.week4.backend.global.exception.InvalidPasswordException;
import com.vani.week4.backend.global.exception.UserNotFoundException;
import com.vani.week4.backend.user.dto.UserResponse;
import com.vani.week4.backend.user.dto.UserUpdateRequest;
import com.vani.week4.backend.user.entity.User;
import com.vani.week4.backend.user.entity.UserStatus;
import com.vani.week4.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author vani
 * @since 10/14/25
 */
@Service
public class UserService {
    private final UserRepository userRepository;
    private final AuthService authService;
    private final S3Service s3Service;
    //순환참조 해결용
    protected UserService(
            UserRepository userRepository,
            @Lazy AuthService authService,
            S3Service s3Service
    ) {
        this.userRepository = userRepository;
        this.authService = authService;
        this.s3Service = s3Service;
    }
    //TODO 테이블 수정 필요 User-UserAuth 이메일....
    public UserResponse getUserInfo(User user) {
        String key = user.getProfileImageKey();
        String email = authService.getEmailFromUserId(user.getId());
        String presignedGetUrl = (key != null && !key.isBlank())
                ? s3Service.createPresignedGetUrl(key)
                : null;
        return new UserResponse(user.getNickname(), email, presignedGetUrl);
    }

    @Transactional
    public void updateUser(User user, UserUpdateRequest request) {

        if (request.nickname() != null) {
            user.updateNickname(request.nickname());
        }

        if (request.profileImageKey() != null) {
            user.updateProfileImageKey(request.profileImageKey());
        }
    }

    @Transactional
    public User createUser(String userId, SignUpRequest signUpRequest) {
        User user = User.createUser(
                userId,
                signUpRequest.nickname(),
                signUpRequest.profileImageKey()
        );
        userRepository.save(user);
        return user;
    }

    /**
     * 유저를 소프트 delete하는 메서드
     * */
    // TODO : 실제 삭제를 배치 처리해야함
    @Transactional
    public void withdrawUser(String userId, WithdrawRequest request){
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(ErrorCode.RESOURCE_NOT_FOUND));

        authService.checkPassword(user, request.password());

        user.updateUserStatus(UserStatus.DELETED);
    }
}
