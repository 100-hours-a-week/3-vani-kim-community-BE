package com.vani.week4.backend.auth.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.servlet.Filter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.core.Ordered;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;

/**
 * 서블릿 필터 설정을 위한 클래스
 * JWT 인증 필터와 CORS설정 핉터를 등록하고 순서관리
 * @author vani
 * @since 10/30/25
 */

@Configuration
@RequiredArgsConstructor
public class WebFilterConfig {
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    // JWT인증 필터
    @Bean
    public FilterRegistrationBean<Filter> jwtFilter() {
        //커스텀 필터를 서블릿 컨테이너에 명시적으로 등록하기
        FilterRegistrationBean<Filter> filterRegistrationBean = new FilterRegistrationBean<>();
        //JWT를 검증하는 필터 인스턴스를 체인에 연결
        filterRegistrationBean.setFilter(jwtAuthenticationFilter);
        //모든 요청 경로에 필터가 적용되도록
        filterRegistrationBean.addUrlPatterns("/*");
        //필터 실행 순서를 지정해, 다른 필터보다 우선 실행되게(인증 인가 시켜야 들어오는거지)
        filterRegistrationBean.setOrder(1);
        return filterRegistrationBean;
    }

    // CORS 필터, 프론트 엔드와 크로스 오리진 허용
    @Bean
    public FilterRegistrationBean<CorsFilter> corsFilterRegistrationBean() {
        CorsConfiguration config = new CorsConfiguration();

        //쿠키 교환 가능하게
        config.setAllowCredentials(true);
        //프론트엔트 출처 허용하기
        config.setAllowedOrigins(Arrays.asList(
                "http://localhost:3000",
                "http://127.0.0.1:3000",
                "https://vanicommu.click"
        ));
        //허용할 헤더
        config.addAllowedHeader("*");
        //허용할 메서드
        config.addAllowedMethod("*");
        //헤더 읽기 허용
        config.addExposedHeader("Authorization");

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        FilterRegistrationBean<CorsFilter> filterBean = new FilterRegistrationBean<>(new CorsFilter(source));

        // CorsFilter가 가장 먼저 실행 되도록
        filterBean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return filterBean;
    }
}