package com.vani.week4.backend.auth.service;
import com.vani.week4.backend.auth.dto.request.*;
import com.vani.week4.backend.auth.dto.response.LoginResponse;
import com.vani.week4.backend.auth.dto.response.TokenResponse;
import com.vani.week4.backend.auth.dto.response.SignUpResponse;
import com.vani.week4.backend.auth.entity.Auth;
import com.vani.week4.backend.auth.entity.ProviderType;
import com.vani.week4.backend.auth.repository.AuthRepository;
import com.vani.week4.backend.auth.security.JwtTokenProvider;
import com.vani.week4.backend.global.ErrorCode;
import com.vani.week4.backend.global.exception.*;
import com.vani.week4.backend.user.dto.PasswordUpdateRequest;
import com.vani.week4.backend.user.entity.User;
import com.vani.week4.backend.user.repository.UserRepository;
import com.vani.week4.backend.user.service.UserService;
import io.jsonwebtoken.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.github.f4b6a3.ulid.UlidCreator;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;

/**
 * @author vani
 * @since 10/9/25
 */

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthRepository authRepository;
    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;
    private final RedisTemplate<String,String> redisTemplate;
    private final UserService userService;
    /**
     * 회원가입을 진행하는 메서드
     * @param signUpRequest : 회원가입 정보(이메일, 비밀번호, 닉네임, 프로필 이미지 url)
     * @return : 인증용 토큰 발급
     * */
    // TODO : OAUTH, 소셜로그인 도입
    // TODO : local로 가입시 중복이메일 확인 내부로직
    @Transactional
    public SignUpResponse signUp(SignUpRequest signUpRequest){
        // User 생성 요청
        String userId = UlidCreator.getUlid().toString();

        User user = userService.createUser(userId, signUpRequest);

        //Auth 생성 요청
        Auth auth = Auth.ceateAuth(
                user,
                userId,
                signUpRequest.email(),
                ProviderType.LOCAL,
                passwordEncoder.encode(signUpRequest.password())
        );
        authRepository.save(auth);

        return new SignUpResponse(userId);
    }

    /**
     * Access,Refresh 토큰을 발급하는 로그인 메서드
     * @param request : 로그인 요청(이메일, 비밀번호)
     * @return : 인증용 토큰 발급
     * */
    // TODO : 삭제 후 재로그인 전략 필요
    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        // 인증 정보 확인
        Auth auth = authRepository.findByEmail(request.email())
                .orElseThrow(() -> new UserNotFoundException(ErrorCode.RESOURCE_NOT_FOUND));
        // 비밀번호 확인
        if (!passwordEncoder.matches(request.password(), auth.getPasswordHash())){
            throw new InvalidPasswordException(ErrorCode.RESOURCE_CONFLICT);
        }

        //유저 프로필 및 상태 확인
        User user = auth.getUser();
        if(user == null){
            throw new IllegalStateException("인증 정보에 연결된 유저 정보가 없습니다.");
        }
        if(user.isDeleted()){
            throw new UserDeletedException(ErrorCode.FORBIDDEN);
        }
        if (!user.isActive()) {
            throw new UserAccessDeniedException(ErrorCode.FORBIDDEN);
        }

        String userId = user.getId();
        String nickname = user.getNickname();
        //토큰생성
        String accessToken = jwtTokenProvider.generateAccessToken(userId, user.getUserRole());
        String refreshToken = jwtTokenProvider.generateRefreshToken(userId);
        try {
            redisTemplate.opsForValue().set(
                    userId,
                    refreshToken,
                    Duration.ofDays(14)
            );
        } catch (Exception e) {
            log.error("Redis에 리프레시 토큰 저장 실패. UserId: {}", userId, e);
        }

        return LoginResponse.of(
                accessToken,
                refreshToken,
                nickname
        );
    }

    /**
     * refresh토큰을 이용하여 access토큰을 재발급 하는 메서드
     * @param refreshToken : 요청자가 전송한 refresh 토큰
     * */
    // TODO : 저장된 토큰 블랙리스트 처리 로직 필요
    public TokenResponse reissueTokens(String refreshToken) {
        String userId = getUserIdFromToken(refreshToken);

        log.info("===== 🔄 토큰 갱신 시도 시작: UserId [{}] =====", userId);

        //레디스에서 리프레시 토큰 조회
        String storedRefreshToken = redisTemplate.opsForValue().get(userId);

        if (storedRefreshToken == null || !storedRefreshToken.equals(refreshToken)) {
            if (storedRefreshToken == null) {
                log.warn("🚨 갱신 실패: Redis에 [{}] 키로 저장된 토큰이 없습니다. (이미 로그아웃/만료됨)", userId);
            } else {
                log.warn("🚨 갱신 실패: 전달된 토큰과 저장된 토큰이 불일치합니다. (Stale Token 가능성)");
            }
            throw new InvalidTokenException(ErrorCode.UNAUTHORIZED);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(ErrorCode.RESOURCE_NOT_FOUND));

        // 토큰 rotation, access, refresh 토큰 모두 발급
        String newAccessToken = jwtTokenProvider.generateAccessToken(userId, user.getUserRole());
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(userId);

        //레디스에 새로운 리프레시 토큰 추가
        try {
            redisTemplate.opsForValue().set(
                    userId,
                    newRefreshToken,
                    Duration.ofDays(14)
            );
        } catch (Exception e) {
            log.error("Redis에 리프레시 토큰 저장 실패. UserId: {}", userId, e);
            throw new TokenSaveException("리프레시 토큰 저장 중 오류 발생, 로그아웃", ErrorCode.UNAUTHORIZED);
        }
        return new TokenResponse(newAccessToken, newRefreshToken);
    }


    /**
     * 토큰을 폐기하는 메서드
     * @param refreshToken : 요청자에게 전달 받은 refresh 토큰
     * */
    // TODO 인증 서비스가 아니면 어디서 할지 확인. 프론트에서 해야하는지 고민
    public void deleteToken(String refreshToken) {
        String userId = getUserIdFromToken(refreshToken);

        try{
            Boolean result = redisTemplate.delete(userId); // 키는 userId

            if (result) {
                log.info("리프레시 토큰 삭제 성공. UserId: {}", userId);
            } else {
                log.info("삭제할 리프레시 토큰 없음. UserId: {}", userId);
            }
        } catch (Exception e) {
            log.error("Redis에서 토큰 삭제 실패. UserId: {}", userId, e);
        }
    }

    /**
     * 이메일 중복확인 메서드
     * */
    public void checkDuplicatedEmail(CheckEmailRequest request){
        boolean isDuplicated = authRepository.existsByEmail(request.email());
        if(isDuplicated) {
            throw new EmailAlreadyExistsException(ErrorCode.RESOURCE_CONFLICT);
        }
    }

    /**
     * 닉네임 중복 확인 메서드
     * */
    public void checkDuplicatedNickname(CheckNicknameRequest request){
        boolean isDuplicated = userRepository.existsByNickname(request.nickname());
        if(isDuplicated) {
            throw new NicknameAlreadyExistsException(ErrorCode.RESOURCE_CONFLICT);
        }
    }

    /**
     * 디비에 저장된 비밀번호와 확인하는 메서드
     * */
    public void checkPassword(User user, String password){
        Auth auth = authRepository.findByUserAndProvider(user, ProviderType.LOCAL)
                .orElseThrow(() -> new AuthNotFoundException(ErrorCode.RESOURCE_NOT_FOUND));

        String E_PASSWORD = auth.getPasswordHash();
        boolean passwordMatches = passwordEncoder.matches(password, E_PASSWORD);
        if (!passwordMatches) {
            throw new InvalidPasswordException(ErrorCode.RESOURCE_CONFLICT);
        }
    }

    //TODO 전에 사용한적 있는 비번 방지
    /**
     * 비밀번호를 수정하는 메서드
     * */
    @Transactional
    public void updatePassword(User user, PasswordUpdateRequest request){
        Auth auth = authRepository.findByUserAndProvider(user, ProviderType.LOCAL)
                .orElseThrow(() -> new AuthNotFoundException(ErrorCode.RESOURCE_NOT_FOUND));

        auth.setPasswordHash(passwordEncoder.encode(request.password()));
    }

    /**
     * 토큰에서 유저 아이디를 가져오는 메서드
     * */
    // TODO currentUser 어노테이션과 하는일이 같음 변경 필요
    private String getUserIdFromToken(String token){
        Claims claims;
        //토큰 자체 유효성 검사
        try {
            claims = jwtTokenProvider.parse(token).getBody();
            log.info("파싱중 오류 발생");
        } catch (ExpiredJwtException e) {
            //리프레시 토큰 만료
            throw new InvalidTokenException(ErrorCode.UNAUTHORIZED);
        } catch (JwtException | IllegalArgumentException e) {
            //유효하지 않은 리프레시 토큰
            throw new InvalidTokenException(ErrorCode.UNAUTHORIZED);
        }

        return claims.getSubject();
    }
}