package com.im.gateway.controller;

import com.im.gateway.sse.SseMessagePusher;
import com.im.group.api.GroupInfo;
import com.im.group.api.GroupMemberService;
import com.im.group.api.GroupService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/groups")
public class GroupController {

    private final GroupService groupService;
    private final GroupMemberService groupMemberService;
    private final SseMessagePusher sseMessagePusher;

    public GroupController(GroupService groupService,
                           GroupMemberService groupMemberService,
                           SseMessagePusher sseMessagePusher) {
        this.groupService = groupService;
        this.groupMemberService = groupMemberService;
        this.sseMessagePusher = sseMessagePusher;
    }

    @PostMapping
    public ResponseEntity<GroupInfo> createGroup(@RequestBody Map<String, Object> body) {
        Long creatorId = Long.valueOf(body.get("creatorId").toString());
        String name = (String) body.get("name");
        return ResponseEntity.ok(groupService.createGroup(creatorId, name));
    }

    @GetMapping("/{groupId}")
    public ResponseEntity<GroupInfo> getGroup(@PathVariable("groupId") Long groupId) {
        GroupInfo info = groupService.getGroupInfo(groupId);
        if (info == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(info);
    }

    @PostMapping("/{groupId}/members")
    public ResponseEntity<?> joinGroup(@PathVariable("groupId") Long groupId, @RequestBody Map<String, Object> body) {
        Long userId = Long.valueOf(body.get("userId").toString());
        if (!groupService.exists(groupId)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Group not found"));
        }
        groupMemberService.joinGroup(groupId, userId);

        List<Long> members = groupMemberService.getMemberIds(groupId);
        for (Long memberId : members) {
            if (!memberId.equals(userId)) {
                sseMessagePusher.pushToUser(memberId, Map.of(
                        "type", "groupNotice",
                        "groupId", groupId,
                        "noticeType", 1,
                        "operatorId", userId,
                        "targetUserId", userId
                ));
            }
        }
        return ResponseEntity.ok(Map.of("success", true));
    }

    @DeleteMapping("/{groupId}/members/{userId}")
    public ResponseEntity<?> leaveGroup(@PathVariable("groupId") Long groupId, @PathVariable("userId") Long userId) {
        groupMemberService.leaveGroup(groupId, userId);

        List<Long> members = groupMemberService.getMemberIds(groupId);
        sseMessagePusher.pushToUsers(members, Map.of(
                "type", "groupNotice",
                "groupId", groupId,
                "noticeType", 2,
                "operatorId", userId,
                "targetUserId", userId
        ));
        return ResponseEntity.ok(Map.of("success", true));
    }

    @DeleteMapping("/{groupId}/members/{targetUserId}/kick")
    public ResponseEntity<?> kickMember(@PathVariable("groupId") Long groupId,
                                         @PathVariable("targetUserId") Long targetUserId,
                                         @RequestParam("operatorId") Long operatorId) {
        try {
            groupMemberService.kickMember(groupId, operatorId, targetUserId);

            List<Long> members = groupMemberService.getMemberIds(groupId);
            sseMessagePusher.pushToUsers(members, Map.of(
                    "type", "groupNotice",
                    "groupId", groupId,
                    "noticeType", 3,
                    "operatorId", operatorId,
                    "targetUserId", targetUserId
            ));
            return ResponseEntity.ok(Map.of("success", true));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{groupId}")
    public ResponseEntity<?> dissolveGroup(@PathVariable("groupId") Long groupId, @RequestParam("operatorId") Long operatorId) {
        try {
            List<Long> membersBefore = groupMemberService.getMemberIds(groupId);
            groupService.dissolveGroup(groupId, operatorId);

            sseMessagePusher.pushToUsers(membersBefore, Map.of(
                    "type", "groupNotice",
                    "groupId", groupId,
                    "noticeType", 4,
                    "operatorId", operatorId
            ));
            return ResponseEntity.ok(Map.of("success", true));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{groupId}/members")
    public ResponseEntity<List<Long>> getMembers(@PathVariable("groupId") Long groupId) {
        return ResponseEntity.ok(groupMemberService.getMemberIds(groupId));
    }

}
