package com.yeogido.backend.global.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yeogido.backend.domain.auth.exception.AuthErrorCode;
import com.yeogido.backend.domain.auth.security.JwtAuthenticationFilter;
import com.yeogido.backend.global.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.nio.charset.StandardCharsets;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String[] SWAGGER_PATHS = {
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/v3/api-docs/**",
            "/swagger-resources/**",
            "/webjars/**"
    };

    private static final String[] AUTHENTICATED_GET_PATHS = {
            "/api/v1/users/me",
            "/api/v1/users/me/**",
            "/api/v1/courses/*/summary",
            "/api/v1/business-promotions/me",
            "/api/v1/travel-records",
            "/api/v1/travel-records/**",
            "/api/v1/stickers",
            "/api/v1/stickers/**"
    };

    private static final String[] AUTHENTICATED_POST_PATHS = {
            "/api/v1/auth/logout",
            "/api/v1/files/presigned-url",
            "/api/v1/courses",
            "/api/v1/courses/*/reviews",
            "/api/v1/users/business-verify",
            "/api/v1/business-promotions",
            "/api/v1/contents",
            "/api/v1/travel-records",
            "/api/v1/stickers"
    };

    private static final String[] AUTHENTICATED_PATCH_PATHS = {
            "/api/v1/users/me",
            "/api/v1/courses/**",
            "/api/v1/reviews/**",
            "/api/v1/business-promotions/**",
            "/api/v1/contents/*",
            "/api/v1/travel-records/**"
    };

    private static final String[] AUTHENTICATED_PUT_PATHS = {
            "/api/v1/courses/*/likes",
            "/api/v1/contents/*/likes",
            "/api/v1/places/*/likes"
    };

    private static final String[] AUTHENTICATED_DELETE_PATHS = {
            "/api/v1/users/me",
            "/api/v1/courses/**",
            "/api/v1/courses/*/likes",
            "/api/v1/reviews/**",
            "/api/v1/business-promotions/**",
            "/api/v1/contents/*",
            "/api/v1/places/*/likes",
            "/api/v1/travel-records/**",
            "/api/v1/stickers/**"
    };

    private static final String[] PUBLIC_GET_PATHS = {
            "/api/v1/auth/check-email",
            "/api/v1/regions",
            "/api/v1/regions/**",
            "/api/v1/hashtags",
            "/api/v1/courses",
            "/api/v1/courses/*",
            "/api/v1/courses/*/reviews",
            "/api/v1/business-promotions",
            "/api/v1/business-promotions/*",
            "/api/v1/contents",
            "/api/v1/contents/*",
            "/api/v1/reviews",
            "/api/v1/reviews/recent"
    };

    private static final String[] PUBLIC_POST_PATHS = {
            "/api/v1/auth/signup",
            "/api/v1/auth/login",
            "/api/v1/auth/social-login",
            "/api/v1/auth/social-signup/complete",
            "/api/v1/auth/reissue",
            "/api/v1/auth/email/send-code",
            "/api/v1/auth/email/verify-code",
            "/api/v1/auth/password/send-code",
            "/api/v1/auth/password/verify-code",
            "/api/v1/auth/test/token",
            "/api/test/popularity-rankings/refresh"
    };

    private static final String[] PUBLIC_PATCH_PATHS = {
            "/api/v1/auth/password/reset"
    };

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ObjectMapper objectMapper;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(AuthErrorCode.LOGIN_REQUIRED.getHttpStatus().value());
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                            objectMapper.writeValue(response.getWriter(), ApiResponse.onFailure(AuthErrorCode.LOGIN_REQUIRED));
                        })
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(SWAGGER_PATHS).permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                        .requestMatchers("/actuator", "/actuator/**").denyAll()

                        .requestMatchers(HttpMethod.GET, AUTHENTICATED_GET_PATHS).authenticated()
                        .requestMatchers(HttpMethod.POST, AUTHENTICATED_POST_PATHS).authenticated()
                        .requestMatchers(HttpMethod.PUT, AUTHENTICATED_PUT_PATHS).authenticated()
                        .requestMatchers(HttpMethod.PATCH, AUTHENTICATED_PATCH_PATHS).authenticated()
                        .requestMatchers(HttpMethod.DELETE, AUTHENTICATED_DELETE_PATHS).authenticated()

                        .requestMatchers(HttpMethod.GET, PUBLIC_GET_PATHS).permitAll()
                        .requestMatchers(HttpMethod.POST, PUBLIC_POST_PATHS).permitAll()
                        .requestMatchers(HttpMethod.PATCH, PUBLIC_PATCH_PATHS).permitAll()

                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
