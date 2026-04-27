package com.im.user.api;

import com.im.user.api.dto.AuthResult;
import com.im.user.api.dto.LoginRequest;
import com.im.user.api.dto.RegisterRequest;

public interface AuthService {

    AuthResult register(RegisterRequest request);

    AuthResult login(LoginRequest request);

    boolean verifyToken(String token);

    Long extractUserId(String token);
}
