package com.bobfull.auth.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bobfull.auth.dto.LoginRequest;
import com.bobfull.auth.dto.LoginResponse;
import com.bobfull.auth.dto.SignupOwnerRequest;
import com.bobfull.auth.dto.SignupResponse;
import com.bobfull.auth.dto.SignupUserRequest;
import com.bobfull.auth.service.AuthService;
import com.bobfull.common.config.ClockConfig;
import com.bobfull.common.exception.CustomException;
import com.bobfull.common.exception.MemberErrorCode;
import com.bobfull.common.security.MemberRole;
import com.bobfull.common.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;

/**
 * 회원가입·로그인 API의 요청 검증, 응답 형식, 중복·인증 실패 매핑을 검증한다.
 * /api/auth/**는 실제 운영에서 SecurityConfig의 permitAll 대상이라 같은 설정을 Import해 검증한다.
 */
@WebMvcTest(controllers = AuthController.class)
@Import({SecurityConfig.class, ClockConfig.class})
@TestPropertySource(properties = {
        "jwt.secret=auth-controller-web-test-secret-key-please-keep-this-long-enough",
        "jwt.access-token-expiration-seconds=3600"
})
class AuthControllerWebTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthService authService;

    @Test
    void 일반_회원가입_성공시_201과_MEMBER_역할을_반환한다() throws Exception {
        // given
        SignupUserRequest request = new SignupUserRequest("user@example.com", "Password123!", "홍길동", "01012345678");
        given(authService.signupMember(request))
                .willReturn(new SignupResponse(1L, "user@example.com", "홍길동", MemberRole.MEMBER));

        // when
        ResultActions result = mockMvc.perform(post("/api/auth/signup/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));

        // then
        result.andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.role", is("MEMBER")));
    }

    @Test
    void 이메일_형식이_올바르지_않으면_400을_반환한다() throws Exception {
        // given
        String invalidBody = """
                {"email":"not-an-email","password":"Password123!","name":"홍길동","phoneNumber":"01012345678"}
                """;

        // when
        ResultActions result = mockMvc.perform(post("/api/auth/signup/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidBody));

        // then
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("INVALID_INPUT_VALUE")));
    }

    @Test
    void 이메일이_중복되면_409와_DUPLICATE_EMAIL을_반환한다() throws Exception {
        // given
        SignupUserRequest request = new SignupUserRequest("dup@example.com", "Password123!", "홍길동", "01012345678");
        given(authService.signupMember(request)).willThrow(new CustomException(MemberErrorCode.DUPLICATE_EMAIL));

        // when
        ResultActions result = mockMvc.perform(post("/api/auth/signup/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));

        // then
        result.andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("DUPLICATE_EMAIL")));
    }

    @Test
    void 사장님_회원가입_성공시_201과_OWNER_역할을_반환한다() throws Exception {
        // given
        SignupOwnerRequest request = new SignupOwnerRequest(
                "owner@example.com", "Password123!", "김사장", "01012345678", "1234567890");
        given(authService.signupOwner(request))
                .willReturn(new SignupResponse(2L, "owner@example.com", "김사장", MemberRole.OWNER));

        // when
        ResultActions result = mockMvc.perform(post("/api/auth/signup/owners")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));

        // then
        result.andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.role", is("OWNER")));
    }

    @Test
    void 로그인_성공시_AccessToken을_반환한다() throws Exception {
        // given
        LoginRequest request = new LoginRequest("user@example.com", "Password123!");
        given(authService.login(request)).willReturn(LoginResponse.of("access-token"));

        // when
        ResultActions result = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken", is("access-token")))
                .andExpect(jsonPath("$.data.tokenType", is("Bearer")));
    }

    @Test
    void 로그인_실패시_401과_INVALID_CREDENTIALS를_반환한다() throws Exception {
        // given
        LoginRequest request = new LoginRequest("user@example.com", "WrongPassword!");
        given(authService.login(request)).willThrow(new CustomException(MemberErrorCode.INVALID_CREDENTIALS));

        // when
        ResultActions result = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));

        // then
        result.andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", is("INVALID_CREDENTIALS")));
    }
}
