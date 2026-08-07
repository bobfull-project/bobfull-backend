package com.bobfull.common.security;

import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 요청의 Authorization 헤더에서 Access Token을 추출·검증해 SecurityContext에 AuthMember를 등록한다.
 * 토큰이 없거나 유효하지 않으면 인증을 설정하지 않고 다음 필터로 넘겨,
 * 보호 API는 AuthenticationEntryPoint가 401로 응답하도록 한다.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String token = resolveToken(request);

        if (token != null) {
            try {
                AuthMember authMember = jwtTokenProvider.parseAccessToken(token);
                SecurityContextHolder.getContext().setAuthentication(createAuthentication(authMember));
            } catch (InvalidJwtException e) {
                SecurityContextHolder.clearContext();
                if (!isTokenExpired(e)) {
                    log.warn("event=JWT_INVALID reason=INVALID_JWT path={}", request.getRequestURI());
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean isTokenExpired(InvalidJwtException exception) {
        return exception.getCause() instanceof ExpiredJwtException;
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(AUTHORIZATION_HEADER);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        return null;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return "/api/webhooks/portone".equals(request.getRequestURI());
    }

    private Authentication createAuthentication(AuthMember authMember) {
        SimpleGrantedAuthority authority = new SimpleGrantedAuthority("ROLE_" + authMember.role().name());
        return new UsernamePasswordAuthenticationToken(authMember, null, List.of(authority));
    }
}
