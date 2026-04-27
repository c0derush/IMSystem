package com.im.user.service;

import com.im.user.api.AuthService;
import com.im.user.api.dto.AuthResult;
import com.im.user.api.dto.LoginRequest;
import com.im.user.api.dto.RegisterRequest;
import com.im.user.entity.User;
import com.im.user.repository.UserRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AuthServiceImpl(UserRepository userRepository, JwtService jwtService) {
        this.userRepository = userRepository;
        this.jwtService = jwtService;
    }

    @Override
    public AuthResult register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            return AuthResult.fail("Username already exists");
        }
        User user = new User();
        user.setUsername(request.username());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setNickname(request.nickname() != null ? request.nickname() : request.username());
        userRepository.save(user);

        String token = jwtService.generateToken(user.getUsername(), user.getId());
        return AuthResult.ok(token, user.getId(), user.getNickname());
    }

    @Override
    public AuthResult login(LoginRequest request) {
        User user = userRepository.findByUsername(request.username())
                .orElse(null);
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            return AuthResult.fail("Invalid username or password");
        }
        String token = jwtService.generateToken(user.getUsername(), user.getId());
        return AuthResult.ok(token, user.getId(), user.getNickname());
    }

    @Override
    public boolean verifyToken(String token) {
        return jwtService.isTokenValid(token);
    }

    @Override
    public Long extractUserId(String token) {
        return jwtService.extractUserId(token);
    }
}
