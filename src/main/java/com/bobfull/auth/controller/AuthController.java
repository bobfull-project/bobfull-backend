package com.bobfull.auth.controller;

import com.bobfull.auth.dto.LoginRequest;
import com.bobfull.auth.dto.LoginResponse;
import com.bobfull.auth.dto.LogoutResponse;
import com.bobfull.auth.dto.ReissueRequest;
import com.bobfull.auth.dto.ReissueResponse;
import com.bobfull.auth.dto.SignupOwnerRequest;
import com.bobfull.auth.dto.SignupResponse;
import com.bobfull.auth.dto.SignupUserRequest;
import com.bobfull.auth.service.AuthService;
import com.bobfull.common.response.ApiResponse;
import com.bobfull.common.security.AuthMember;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/signup/users")
    public ResponseEntity<ApiResponse<SignupResponse>> signupUser(@Valid @RequestBody SignupUserRequest request) {
        SignupResponse response = authService.signupMember(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @PostMapping("/signup/owners")
    public ResponseEntity<ApiResponse<SignupResponse>> signupOwner(@Valid @RequestBody SignupOwnerRequest request) {
        SignupResponse response = authService.signupOwner(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/reissue")
    public ResponseEntity<ApiResponse<ReissueResponse>> reissue(@Valid @RequestBody ReissueRequest request) {
        ReissueResponse response = authService.reissue(request.refreshToken());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<LogoutResponse>> logout(@AuthenticationPrincipal AuthMember authMember) {
        LogoutResponse response = authService.logout(authMember.id());
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
