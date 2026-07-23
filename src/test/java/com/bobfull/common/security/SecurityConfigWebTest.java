package com.bobfull.common.security;

import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bobfull.common.support.TestApiController;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * SecurityConfig의 공개/인증 필요 API 구분, 인증 회원 전달, 역할 기반 접근 제어를 검증한다.
 */
@WebMvcTest(controllers = TestApiController.class)
@Import(SecurityConfig.class)
class SecurityConfigWebTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void 공개_API는_인증_없이_접근할_수_있다() throws Exception {
        // when
        ResultActions result = mockMvc.perform(get("/api/public/hello"));

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
}
