package com.bobfull.common.security;

import tools.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * 공개 API와 인증 필요 API를 구분하고, JWT 기반 인증과 역할별 인가를 적용하는 Security 설정이다.
 * 공개 경로는 API 명세의 V1 인증 불필요 경로만 명시적으로 permitAll 처리한다.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public JwtTokenProvider jwtTokenProvider(
            ObjectMapper objectMapper,
            Clock clock,
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-expiration-seconds}") long accessTokenExpirationSeconds
    ) {
        return new JwtTokenProvider(objectMapper, clock, secret, accessTokenExpirationSeconds);
    }

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider) {
        return new JwtAuthenticationFilter(jwtTokenProvider);
    }

    // 로컬 프론트(Vite) 연동 확인용 임시 CORS 설정이다. 커밋하지 않는다.
    // 5173이 이미 점유돼 있으면 Vite가 5174로 자동 이동하므로 둘 다 허용한다.
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of("http://localhost:5173", "http://localhost:5174"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            ObjectMapper objectMapper,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            CorsConfigurationSource corsConfigurationSource
    ) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .httpBasic(httpBasic -> httpBasic.disable())
                .formLogin(formLogin -> formLogin.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/api/restaurants",
                                "/api/restaurants/{restaurantId}",
                                "/api/restaurants/{restaurantId}/dining-sessions"
                        ).permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/reservations/search").permitAll()
                        .requestMatchers("/api/owner/**").hasRole("OWNER")
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(exceptionHandling -> exceptionHandling
                        .authenticationEntryPoint(new CustomAuthenticationEntryPoint(objectMapper))
                        .accessDeniedHandler(new CustomAccessDeniedHandler(objectMapper))
                );

        return http.build();
    }
}
