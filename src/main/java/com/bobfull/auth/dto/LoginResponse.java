package com.bobfull.auth.dto;

public record LoginResponse(String accessToken, String tokenType) {

    private static final String BEARER_TOKEN_TYPE = "Bearer";

    public static LoginResponse of(String accessToken) {
        return new LoginResponse(accessToken, BEARER_TOKEN_TYPE);
    }
}
