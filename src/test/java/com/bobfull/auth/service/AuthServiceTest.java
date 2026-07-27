package com.bobfull.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.bobfull.auth.dto.LoginRequest;
import com.bobfull.auth.dto.LoginResponse;
import com.bobfull.auth.dto.SignupOwnerRequest;
import com.bobfull.auth.dto.SignupResponse;
import com.bobfull.auth.dto.SignupUserRequest;
import com.bobfull.common.exception.CustomException;
import com.bobfull.common.exception.MemberErrorCode;
import com.bobfull.common.security.JwtTokenProvider;
import com.bobfull.common.security.MemberRole;
import com.bobfull.member.entity.Member;
import com.bobfull.member.repository.MemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 회원가입 중복 검증·비밀번호 해시, 로그인 성공·실패 흐름을 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @InjectMocks
    private AuthService authService;

    @Test
    void 이메일이_중복되면_회원가입에_실패한다() {
        // given
        SignupUserRequest request = new SignupUserRequest("dup@example.com", "Password123!", "홍길동", "01011112222");
        given(memberRepository.existsByEmail(request.email())).willReturn(true);

        // when
        Throwable result = catchThrowable(() -> authService.signupMember(request));

        // then
        assertThat(result).isInstanceOf(CustomException.class);
        assertThat(((CustomException) result).getErrorCode()).isEqualTo(MemberErrorCode.DUPLICATE_EMAIL);
    }

    @Test
    void 전화번호가_중복되면_회원가입에_실패한다() {
        // given
        SignupUserRequest request = new SignupUserRequest("new@example.com", "Password123!", "홍길동", "01011112222");
        given(memberRepository.existsByEmail(request.email())).willReturn(false);
        given(memberRepository.existsByPhoneNumber(request.phoneNumber())).willReturn(true);

        // when
        Throwable result = catchThrowable(() -> authService.signupMember(request));

        // then
        assertThat(result).isInstanceOf(CustomException.class);
        assertThat(((CustomException) result).getErrorCode()).isEqualTo(MemberErrorCode.DUPLICATE_PHONE_NUMBER);
    }

    @Test
    void 사업자등록번호가_중복되면_사장님_회원가입에_실패한다() {
        // given
        SignupOwnerRequest request = new SignupOwnerRequest(
                "owner@example.com", "Password123!", "김사장", "01033334444", "1234567890");
        given(memberRepository.existsByEmail(request.email())).willReturn(false);
        given(memberRepository.existsByPhoneNumber(request.phoneNumber())).willReturn(false);
        given(memberRepository.existsByBusinessNumber(request.businessNumber())).willReturn(true);

        // when
        Throwable result = catchThrowable(() -> authService.signupOwner(request));

        // then
        assertThat(result).isInstanceOf(CustomException.class);
        assertThat(((CustomException) result).getErrorCode()).isEqualTo(MemberErrorCode.DUPLICATE_BUSINESS_NUMBER);
    }

    @Test
    void 회원가입_성공시_비밀번호를_해시로_저장한다() {
        // given
        SignupUserRequest request = new SignupUserRequest("new@example.com", "Password123!", "홍길동", "01011112222");
        given(memberRepository.existsByEmail(request.email())).willReturn(false);
        given(memberRepository.existsByPhoneNumber(request.phoneNumber())).willReturn(false);
        given(passwordEncoder.encode(request.password())).willReturn("encoded-password");
        given(memberRepository.save(any(Member.class))).willAnswer(invocation -> invocation.getArgument(0));

        // when
        SignupResponse response = authService.signupMember(request);

        // then
        assertThat(response.email()).isEqualTo(request.email());
        assertThat(response.role()).isEqualTo(MemberRole.MEMBER);
        verify(passwordEncoder).encode(request.password());
    }

    @Test
    void 사전_검사_통과후_저장시점에_DB_제약을_위반하면_중복_이메일_예외로_변환한다() {
        // given
        SignupUserRequest request = new SignupUserRequest("race@example.com", "Password123!", "홍길동", "01011112222");
        given(memberRepository.existsByEmail(request.email()))
                .willReturn(false)
                .willReturn(true);
        given(memberRepository.existsByPhoneNumber(anyString())).willReturn(false);
        given(passwordEncoder.encode(anyString())).willReturn("encoded-password");
        given(memberRepository.save(any(Member.class)))
                .willThrow(new DataIntegrityViolationException("duplicate key"));

        // when
        Throwable result = catchThrowable(() -> authService.signupMember(request));

        // then
        assertThat(result).isInstanceOf(CustomException.class);
        assertThat(((CustomException) result).getErrorCode()).isEqualTo(MemberErrorCode.DUPLICATE_EMAIL);
    }

    @Test
    void 존재하지_않는_이메일로_로그인하면_INVALID_CREDENTIALS_예외가_발생한다() {
        // given
        LoginRequest request = new LoginRequest("unknown@example.com", "Password123!");
        given(memberRepository.findByEmail(request.email())).willReturn(java.util.Optional.empty());

        // when
        Throwable result = catchThrowable(() -> authService.login(request));

        // then
        assertThat(result).isInstanceOf(CustomException.class);
        assertThat(((CustomException) result).getErrorCode()).isEqualTo(MemberErrorCode.INVALID_CREDENTIALS);
    }

    @Test
    void 비밀번호가_일치하지_않으면_INVALID_CREDENTIALS_예외가_발생한다() {
        // given
        LoginRequest request = new LoginRequest("user@example.com", "WrongPassword!");
        Member member = Member.createMember("user@example.com", "encoded-password", "홍길동", "01011112222");
        given(memberRepository.findByEmail(request.email())).willReturn(java.util.Optional.of(member));
        given(passwordEncoder.matches(request.password(), member.getPasswordHash())).willReturn(false);

        // when
        Throwable result = catchThrowable(() -> authService.login(request));

        // then
        assertThat(result).isInstanceOf(CustomException.class);
        assertThat(((CustomException) result).getErrorCode()).isEqualTo(MemberErrorCode.INVALID_CREDENTIALS);
    }

    @Test
    void 로그인_성공시_AccessToken을_발급한다() {
        // given
        LoginRequest request = new LoginRequest("user@example.com", "Password123!");
        Member member = Member.createMember("user@example.com", "encoded-password", "홍길동", "01011112222");
        given(memberRepository.findByEmail(request.email())).willReturn(java.util.Optional.of(member));
        given(passwordEncoder.matches(request.password(), member.getPasswordHash())).willReturn(true);
        given(jwtTokenProvider.createAccessToken(member.getId(), member.getRole())).willReturn("access-token");

        // when
        LoginResponse response = authService.login(request);

        // then
        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
    }
}
