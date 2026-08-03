package com.bobfull.auth.dto;

public record LogoutResponse(boolean result) {

    public static LogoutResponse success() {
        return new LogoutResponse(true);
    }
}
