package com.im.gateway.controller;

import com.im.user.api.AuthService;
import com.im.user.api.dto.AuthResult;
import com.im.user.api.dto.LoginRequest;
import com.im.user.api.dto.RegisterRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> body) {
        AuthResult result = authService.register(new RegisterRequest(
                body.get("username"),
                body.get("password"),
                body.get("nickname")
        ));
        if (result.success()) {
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "userId", result.userId(),
                    "token", result.token(),
                    "nickname", result.nickname()
            ));
        }
        return ResponseEntity.badRequest().body(Map.of("success", false, "error", result.errorMessage()));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body) {
        AuthResult result = authService.login(new LoginRequest(
                body.get("username"),
                body.get("password")
        ));
        if (result.success()) {
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "userId", result.userId(),
                    "token", result.token(),
                    "nickname", result.nickname()
            ));
        }
        return ResponseEntity.badRequest().body(Map.of("success", false, "error", result.errorMessage()));
    }
}
