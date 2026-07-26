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
                        .requestMatchers(HttpMethod.GET, "/api/v1/courses/*/summary").authenticated()
                        .requestMatchers(
                                "/api/v1/users/me",
                                "/api/v1/users/me/**",
                                "/api/v1/auth/logout",
                                "/api/v1/contents/*/likes",
                                "/api/v1/places/*/likes"
                        ).authenticated()


                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/courses"
                        ).authenticated()

                        .requestMatchers(
                                HttpMethod.PATCH,
                                "/api/v1/courses/**"
                        ).authenticated()

                        .requestMatchers(
                                HttpMethod.DELETE,
                                "/api/v1/courses/**"
                        ).authenticated()

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/courses/*/reviews"
                        ).authenticated()

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/courses/*/likes"
                        ).authenticated()

                        .requestMatchers(
                                HttpMethod.DELETE,
                                "/api/v1/courses/*/likes"
                        ).authenticated()

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/users/business-verify"
                        ).authenticated()

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/business-promotions"
                        ).authenticated()

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/business-promotions/me"
                        ).authenticated()

                        .requestMatchers(
                                HttpMethod.PATCH,
                                "/api/v1/business-promotions/**"
                        ).authenticated()

                        .requestMatchers(
                                HttpMethod.DELETE,
                                "/api/v1/business-promotions/**"
                        ).authenticated()

                        .anyRequest().permitAll()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
