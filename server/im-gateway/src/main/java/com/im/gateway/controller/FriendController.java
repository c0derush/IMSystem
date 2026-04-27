package com.im.gateway.controller;

import com.im.user.api.FriendService;
import com.im.user.api.UserQueryService;
import com.im.user.api.dto.UserInfo;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/friends")
public class FriendController {

    private final FriendService friendService;
    private final UserQueryService userQueryService;

    public FriendController(FriendService friendService, UserQueryService userQueryService) {
        this.friendService = friendService;
        this.userQueryService = userQueryService;
    }

    @GetMapping
    public ResponseEntity<List<UserInfo>> getFriends(@RequestParam("userId") Long userId) {
        List<Long> friendIds = friendService.getFriendIds(userId);
        return ResponseEntity.ok(userQueryService.getUsersByIds(friendIds));
    }

    @PostMapping
    public ResponseEntity<?> addFriend(@RequestBody Map<String, Object> body) {
        Long userId = Long.valueOf(body.get("userId").toString());
        Long friendId = Long.valueOf(body.get("friendId").toString());
        if (userId.equals(friendId)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Cannot add yourself"));
        }
        if (!userQueryService.exists(friendId)) {
            return ResponseEntity.badRequest().body(Map.of("error", "User not found"));
        }
        friendService.addFriend(userId, friendId);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @DeleteMapping("/{friendId}")
    public ResponseEntity<?> removeFriend(@RequestParam("userId") Long userId,
                                          @PathVariable("friendId") Long friendId) {
        friendService.removeFriend(userId, friendId);
        return ResponseEntity.ok(Map.of("success", true));
    }
}
