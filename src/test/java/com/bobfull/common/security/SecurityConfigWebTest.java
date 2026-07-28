package com.bobfull.common.security;

import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bobfull.common.config.ClockConfig;
import com.bobfull.common.support.TestApiController;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;

/**
 * SecurityConfig의 공개/인증 필요 API 구분, JWT 기반 인증, 역할 기반 접근 제어를 검증한다.
 */
@WebMvcTest(controllers = TestApiController.class)
@Import({SecurityConfig.class, ClockConfig.class})
@TestPropertySource(properties = {
        "jwt.secret=security-config-web-test-secret-key-please-keep-this-long-enough",
        "jwt.access-token-expiration-seconds=3600"
})
@ActiveProfiles("test-api")
class SecurityConfigWebTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void 공개_API는_인증_없이_접근할_수_있다() throws Exception {
        // when
        ResultActions result = mockMvc.perform(get("/api/restaurants"));

        // then
        result.andExpect(status().isOk());
    }

    @Test
    void 경로_변수를_포함한_공개_API도_인증_없이_접근할_수_있다() throws Exception {
        // when
        ResultActions result = mockMvc.perform(get("/api/restaurants/1/dining-sessions"));

        // then
        result.andExpect(status().isOk());
    }

    @Test
    void 예약_검색_공개_API는_인증_없이_접근할_수_있다() throws Exception {
        // when
        ResultActions result = mockMvc.perform(get("/api/reservations/search"));

        // then
        result.andExpect(status().isOk());
    }

    @Test
    void 회원가입_로그인_등_인증_API는_인증_없이_접근할_수_있다() throws Exception {
        // when
        ResultActions result = mockMvc.perform(post("/api/auth/sample"));

        // then
        result.andExpect(status().isOk());
    }

    @Test
    void 인증_필요_API에_인증_없이_접근하면_401_공통_실패_응답을_반환한다() throws Exception {
        // when
        ResultActions result = mockMvc.perform(get("/api/protected/hello"));

        // then
        result.andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.code", is("UNAUTHORIZED")));
    }

    @Test
    void 인증된_회원이_접근하면_AuthMember가_컨트롤러에_전달된다() throws Exception {
        // given
        AuthMember authMember = new AuthMember(1L, MemberRole.MEMBER);
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                authMember, null, List.of(new SimpleGrantedAuthority("ROLE_MEMBER")));

        // when
        ResultActions result = mockMvc.perform(get("/api/protected/hello").with(authentication(authentication)));

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id", is(1)))
                .andExpect(jsonPath("$.data.role", is("MEMBER")));
    }

    @Test
    void 사장님_권한이_없는_회원이_사장님_전용_API에_접근하면_403_공통_실패_응답을_반환한다() throws Exception {
        // given
        AuthMember authMember = new AuthMember(1L, MemberRole.MEMBER);
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                authMember, null, List.of(new SimpleGrantedAuthority("ROLE_MEMBER")));

        // when
        ResultActions result = mockMvc.perform(get("/api/owner/hello").with(authentication(authentication)));

        // then
        result.andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.code", is("ACCESS_DENIED")));
    }

    @Test
    void 사장님_권한을_가진_회원은_사장님_전용_API에_접근할_수_있다() throws Exception {
        // given
        AuthMember authMember = new AuthMember(2L, MemberRole.OWNER);
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                authMember, null, List.of(new SimpleGrantedAuthority("ROLE_OWNER")));

        // when
        ResultActions result = mockMvc.perform(get("/api/owner/hello").with(authentication(authentication)));

        // then
        result.andExpect(status().isOk());
    }

    @Test
    void 관리자_권한이_없는_회원이_관리자_전용_API에_접근하면_403_공통_실패_응답을_반환한다() throws Exception {
        // given
        AuthMember authMember = new AuthMember(1L, MemberRole.MEMBER);
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                authMember, null, List.of(new SimpleGrantedAuthority("ROLE_MEMBER")));

        // when
        ResultActions result = mockMvc.perform(get("/api/admin/hello").with(authentication(authentication)));

        // then
        result.andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.code", is("ACCESS_DENIED")));
    }

    @Test
    void 관리자_권한을_가진_회원은_관리자_전용_API에_접근할_수_있다() throws Exception {
        // given
        AuthMember authMember = new AuthMember(3L, MemberRole.ADMIN);
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                authMember, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

        // when
        ResultActions result = mockMvc.perform(get("/api/admin/hello").with(authentication(authentication)));

        // then
        result.andExpect(status().isOk());
    }

    @Test
    void 유효한_Access_Token이면_AuthMember가_등록되고_보호_API에_접근할_수_있다() throws Exception {
        // given
        String accessToken = jwtTokenProvider.createAccessToken(10L, MemberRole.OWNER);

        // when
        ResultActions result = mockMvc.perform(
                get("/api/protected/hello").header("Authorization", "Bearer " + accessToken));

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id", is(10)))
                .andExpect(jsonPath("$.data.role", is("OWNER")));
    }

    @Test
    void Authorization_헤더가_없으면_보호_API_접근시_401을_반환한다() throws Exception {
        // when
        ResultActions result = mockMvc.perform(get("/api/protected/hello"));

        // then
        result.andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", is("UNAUTHORIZED")));
    }

    @Test
    void 서명이_위조된_토큰이면_보호_API_접근시_401을_반환한다() throws Exception {
        // given
        String accessToken = jwtTokenProvider.createAccessToken(10L, MemberRole.MEMBER);
        String tamperedToken = accessToken.substring(0, accessToken.length() - 1)
                + (accessToken.endsWith("a") ? "b" : "a");

        // when
        ResultActions result = mockMvc.perform(
                get("/api/protected/hello").header("Authorization", "Bearer " + tamperedToken));

        // then
        result.andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", is("UNAUTHORIZED")));
    }

    @Test
    void 만료된_토큰이면_보호_API_접근시_401을_반환한다() throws Exception {
        // given
        Clock pastClock = Clock.fixed(Instant.parse("2020-01-01T00:00:00Z"), ZoneOffset.UTC);
        JwtTokenProvider expiredTokenProvider = new JwtTokenProvider(
                objectMapper, pastClock, "security-config-web-test-secret-key-please-keep-this-long-enough", 1L);
        String expiredToken = expiredTokenProvider.createAccessToken(10L, MemberRole.MEMBER);

        // when
        ResultActions result = mockMvc.perform(
                get("/api/protected/hello").header("Authorization", "Bearer " + expiredToken));

        // then
        result.andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", is("UNAUTHORIZED")));
    }

    @Test
    void 형식이_올바르지_않은_토큰이면_보호_API_접근시_401을_반환한다() throws Exception {
        // when
        ResultActions result = mockMvc.perform(
                get("/api/protected/hello").header("Authorization", "Bearer not-a-jwt"));

        // then
        result.andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", is("UNAUTHORIZED")));
    }
}
