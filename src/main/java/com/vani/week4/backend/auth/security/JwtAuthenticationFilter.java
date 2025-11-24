package com.vani.week4.backend.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vani.week4.backend.user.entity.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.SignatureException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;


/**
 * JWT사용을 위한 커스텀 필터
 * Access 토큰을 검증합니다.
 * @author vani
 * @since 10/14/25
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private static final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final JwtTokenProvider jwtTokenProvider;
    private final ObjectMapper objectMapper;

    //필터 제외 경로 목록
    private static final String[] EXCLUDED_PATHS = {
            "/api/v1/auth/users", "/api/v1/auth/tokens", "/api/v1/auth/nickname", "/api/v1/auth/email",
            "/api/v1/auth/logout", "/api/v1/auth/refresh", "/api/v1/uploads/presign/temp",
            "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**",
            "/api/loadtest/**",  // 부하 테스트 API (개발/테스트 환경 전용)
            "/health"
    };

    // 필터 제외 경로 설정
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();

        return Arrays.stream(EXCLUDED_PATHS)
                .anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    /**
     * JWT 토큰 인증 필터
     * 요청 헤더에서 JWT 토큰을 추출하고 유효성을 검증합니다.
     *
     * @param request HTTP 요청
     * @param response HTTP 응답
     * @param filterChain 필터 체인
     * @throws ServletException 서블릿 예외
     * @throws IOException I/O 예외
     */
    @Override
    protected void doFilterInternal(
            @NonNull  HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        Optional<String> token = extractToken(request);

        // 토큰이 없다면 토큰 없음 오류
        //TODO 블랙리스트 추가시 변경 필수, 권한 설정 필요
        //TODO 글로벌 오류핸들러와 맞춰야함
        if (token.isEmpty()) {
            setErrorResponse(response, "T001", "Token is empty");
            return;
        } else {
            try {
                validateAndSetAttributes(token.get(), request);
            } catch (ExpiredJwtException e) {
                // 토큰 만료시
                setErrorResponse(response, "T002", "Access Token Expired");
                return;
            } catch (SignatureException | MalformedJwtException e) {
                //토큰 위조/형식 오류 예외
                setErrorResponse(response, "T003", "Invalid Token");
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    /**
     * JSON 에러 응답을 직접 만들어주는 헬퍼 메서드
     * 지금 단계에서 에러는 디스패처 서블렛으로 가지 못해서 글로벌 핸들러가 처리할 수 없음
     * */
    private void setErrorResponse(HttpServletResponse response, String errorCode, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED); // 401
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        ErrorResponse errorResponse = new ErrorResponse(errorCode, message);

        String jsonResponse = objectMapper.writeValueAsString(errorResponse);

        response.getWriter().write(jsonResponse);
}

    // 헤더에서 토큰을 추출하는 메서드
    private Optional<String> extractTokenFromHeader(HttpServletRequest request) {
        return Optional.ofNullable(request.getHeader("Authorization"))
                .filter(header -> header.startsWith("Bearer "))
                .map(header -> header.substring(7));
    }

    // 쿠키에서 액세스 토큰을 추출하는 메서드
    private Optional<String> extractTokenFromCookie(HttpServletRequest request) {
        return Optional.ofNullable(request.getCookies())
                .stream()
                .flatMap(Arrays::stream)
                .filter(cookie -> "accessToken".equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst();
    }

    // 토큰을 추출하는 메서드,헤더->쿠키 순
    private Optional<String> extractToken(HttpServletRequest request) {
        return extractTokenFromHeader(request)
                .or(() -> extractTokenFromCookie(request));
    }

    // 토큰 검증 및 요청 속성 설정
    private void validateAndSetAttributes(String token, HttpServletRequest request) {

        var jws = jwtTokenProvider.parse(token);
        Claims body = jws.getBody();  // Claims 내용물.
        request.setAttribute("authenticatedUserId", body.getSubject());

        String roleStr = body.get("role", String.class);

        //Enum값 으로 변경하기
        try {
            UserRole role = UserRole.valueOf(roleStr);
            request.setAttribute("role", role);
        } catch (IllegalArgumentException e) {
            throw new MalformedJwtException("Invalid role Value in Token");
        }
    }
}
