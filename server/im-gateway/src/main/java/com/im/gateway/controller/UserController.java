package com.im.gateway.controller;

import com.im.user.api.UserQueryService;
import com.im.user.api.dto.UserInfo;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserQueryService userQueryService;

    public UserController(UserQueryService userQueryService) {
        this.userQueryService = userQueryService;
    }

    @GetMapping("/{userId}")
    public ResponseEntity<UserInfo> getUser(@PathVariable("userId") Long userId) {
        UserInfo info = userQueryService.getUserById(userId);
        if (info == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(info);
    }
}
