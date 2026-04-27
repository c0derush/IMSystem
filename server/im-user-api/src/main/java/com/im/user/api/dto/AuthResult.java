package com.im.user.api.dto;

public record AuthResult(boolean success, String token, Long userId, String nickname, String errorMessage) {

    public static AuthResult ok(String token, Long userId, String nickname) {
        return new AuthResult(true, token, userId, nickname, null);
    }

    public static AuthResult fail(String errorMessage) {
        return new AuthResult(false, null, null, null, errorMessage);
    }
}
