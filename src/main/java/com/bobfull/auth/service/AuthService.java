package com.bobfull.auth.service;

import com.bobfull.auth.dto.LoginRequest;
import com.bobfull.auth.dto.LoginResponse;
import com.bobfull.auth.dto.SignupOwnerRequest;
import com.bobfull.auth.dto.SignupResponse;
import com.bobfull.auth.dto.SignupUserRequest;
import com.bobfull.common.exception.CustomException;
import com.bobfull.common.exception.MemberErrorCode;
import com.bobfull.common.security.JwtTokenProvider;
import com.bobfull.member.entity.Member;
import com.bobfull.member.repository.MemberRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원가입과 로그인을 담당한다.
 * 이메일·전화번호·사업자등록번호 중복은 저장 전 사전 검사로 우선 차단하고,
 * 동시 가입 경쟁으로 사전 검사를 통과한 뒤 DB UNIQUE 제약에 걸리면
 * DataIntegrityViolationException을 같은 중복 ErrorCode로 변환한다.
 */
@Service
public class AuthService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthService(
            MemberRepository memberRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider jwtTokenProvider
    ) {
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Transactional
    public SignupResponse signupMember(SignupUserRequest request) {
        validateEmailNotDuplicated(request.email());
        validatePhoneNumberNotDuplicated(request.phoneNumber());

        Member member = Member.createMember(
                request.email(),
                passwordEncoder.encode(request.password()),
                request.name(),
                request.phoneNumber()
        );

        Member savedMember = saveOrThrowDuplicate(member, request.email(), request.phoneNumber(), null);
        return SignupResponse.from(savedMember);
    }

    @Transactional
    public SignupResponse signupOwner(SignupOwnerRequest request) {
        validateEmailNotDuplicated(request.email());
        validatePhoneNumberNotDuplicated(request.phoneNumber());
        validateBusinessNumberNotDuplicated(request.businessNumber());

        Member member = Member.createOwner(
                request.email(),
                passwordEncoder.encode(request.password()),
                request.name(),
                request.phoneNumber(),
                request.businessNumber()
        );

        Member savedMember = saveOrThrowDuplicate(member, request.email(), request.phoneNumber(), request.businessNumber());
        return SignupResponse.from(savedMember);
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        Member member = memberRepository.findByEmail(request.email())
                .orElseThrow(() -> new CustomException(MemberErrorCode.INVALID_CREDENTIALS));

        if (!passwordEncoder.matches(request.password(), member.getPasswordHash())) {
            throw new CustomException(MemberErrorCode.INVALID_CREDENTIALS);
        }

        String accessToken = jwtTokenProvider.createAccessToken(member.getId(), member.getRole());
        return LoginResponse.of(accessToken);
    }

    private Member saveOrThrowDuplicate(Member member, String email, String phoneNumber, String businessNumber) {
        try {
            return memberRepository.save(member);
        } catch (DataIntegrityViolationException e) {
            throw resolveDuplicateException(email, phoneNumber, businessNumber, e);
        }
    }

    private CustomException resolveDuplicateException(
            String email,
            String phoneNumber,
            String businessNumber,
            DataIntegrityViolationException cause
    ) {
        if (memberRepository.existsByEmail(email)) {
            return new CustomException(MemberErrorCode.DUPLICATE_EMAIL);
        }
        if (memberRepository.existsByPhoneNumber(phoneNumber)) {
            return new CustomException(MemberErrorCode.DUPLICATE_PHONE_NUMBER);
        }
        if (businessNumber != null && memberRepository.existsByBusinessNumber(businessNumber)) {
            return new CustomException(MemberErrorCode.DUPLICATE_BUSINESS_NUMBER);
        }
        throw cause;
    }

    private void validateEmailNotDuplicated(String email) {
        if (memberRepository.existsByEmail(email)) {
            throw new CustomException(MemberErrorCode.DUPLICATE_EMAIL);
        }
    }

    private void validatePhoneNumberNotDuplicated(String phoneNumber) {
        if (memberRepository.existsByPhoneNumber(phoneNumber)) {
            throw new CustomException(MemberErrorCode.DUPLICATE_PHONE_NUMBER);
        }
    }

    private void validateBusinessNumberNotDuplicated(String businessNumber) {
        if (memberRepository.existsByBusinessNumber(businessNumber)) {
            throw new CustomException(MemberErrorCode.DUPLICATE_BUSINESS_NUMBER);
        }
    }
}
