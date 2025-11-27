package com.vani.week4.backend.auth.security;

import com.vani.week4.backend.user.entity.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * JWT 토큰을 발행하는 클래스
 * @author vani
 * @since 10/13/25
 */
@Slf4j
@Component
public class JwtTokenProvider {
    private final Key key;
    private final long accessExpirationsMs;
    private final long refreshExpirationsMs;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secretKey,
            @Value("${jwt.access-expiration-ms}") long accessExpirationMs,
            @Value("${jwt.refresh-expiration-ms}") long refreshExpirationMs
    ) {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        this.key = Keys.hmacShaKeyFor(keyBytes); //키 객체 생성
        this.accessExpirationsMs = accessExpirationMs;
        this.refreshExpirationsMs = refreshExpirationMs;
        log.info("JwtTokenProvider initialized.");
        log.info("  -> Access Token Expiration: {} ms ({} seconds)",
                this.accessExpirationsMs, TimeUnit.MILLISECONDS.toSeconds(this.accessExpirationsMs));
        log.info("  -> Refresh Token Expiration: {} ms ({} seconds)",
                this.refreshExpirationsMs, TimeUnit.MILLISECONDS.toSeconds(this.refreshExpirationsMs));
    }


    /**
     * Access토큰을 생성하는 메서드
     *
     * @param userId: 토큰 주체(사용자 ID)
     * @param role : 유저의 권한
     * @return Access 토큰 반환(JWT)
     * */
    public String generateAccessToken(String userId, UserRole role) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + accessExpirationsMs);

        log.info("✅ Generating Access Token (UserId: {}). Expires in: {} seconds",
                userId, TimeUnit.MILLISECONDS.toSeconds(accessExpirationsMs));

        return Jwts.builder()
                .setSubject(userId)
                .claim("role", role)
                .setIssuedAt(now)
                .setExpiration(expiration)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * Refresh 토큰을 생성하는 메서드
     * @param userId: 토큰 주체 (사용자 ID)
     * @return 생성된 Refresh 토큰 반환(JWT)
     * */
    public String generateRefreshToken(String userId) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + refreshExpirationsMs);

        log.info("✅ Generating Refresh Token (UserId: {}). Expires in: {} seconds",
                userId, TimeUnit.MILLISECONDS.toSeconds(refreshExpirationsMs));

        return Jwts.builder()
                .setSubject(userId)
                .claim("typ", "refresh")
                .setId(UUID.randomUUID().toString()) // 토큰 고유 식별자
                .setIssuedAt(now)
                .setExpiration(expiration)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }
    //TODO 합치는 방법에 대해 고려
    /**
     * 리프레시 토큰 검증 메서드
     * */
    public Jws<Claims> parse(String token) {
        return Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token);
    }
}