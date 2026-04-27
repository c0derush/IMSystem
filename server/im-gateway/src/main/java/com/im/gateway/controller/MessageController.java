package com.im.gateway.controller;

import com.im.gateway.sse.SseMessagePusher;
import com.im.group.api.GroupMemberService;
import com.im.group.api.GroupMessageDistributor;
import com.im.message.api.MessageService;
import com.im.message.api.dto.Message;
import com.im.push.api.PushService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/messages")
public class MessageController {

    private final MessageService messageService;
    private final PushService pushService;
    private final GroupMemberService groupMemberService;
    private final GroupMessageDistributor groupMessageDistributor;
    private final SseMessagePusher sseMessagePusher;

    public MessageController(MessageService messageService,
                             PushService pushService,
                             GroupMemberService groupMemberService,
                             GroupMessageDistributor groupMessageDistributor,
                             SseMessagePusher sseMessagePusher) {
        this.messageService = messageService;
        this.pushService = pushService;
        this.groupMemberService = groupMemberService;
        this.groupMessageDistributor = groupMessageDistributor;
        this.sseMessagePusher = sseMessagePusher;
    }

    @PostMapping("/group")
    public ResponseEntity<?> sendGroupMessage(@RequestBody Map<String, Object> body) {
        Long senderId = Long.valueOf(body.get("senderId").toString());
        Long groupId = Long.valueOf(body.get("groupId").toString());
        String content = (String) body.get("content");

        if (!groupMemberService.isMember(groupId, senderId)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Not a group member"));
        }

        Message message = messageService.saveMessage(new Message(
                null, senderId, 0L, groupId, content, System.currentTimeMillis(), false, null));

        List<Long> receiverIds = groupMessageDistributor.distribute(groupId, senderId);

        Map<String, Object> payload = Map.of(
                "type", "groupMsg",
                "messageId", message.messageId(),
                "senderId", message.senderId(),
                "groupId", message.groupId(),
                "content", message.content(),
                "timestamp", message.timestamp()
        );

        if (!receiverIds.isEmpty()) {
            pushService.pushGroup(groupId, message, receiverIds);
            sseMessagePusher.pushToUsers(receiverIds, payload);
        }

        return ResponseEntity.ok(Map.of(
                "success", true,
                "messageId", message.messageId(),
                "receiverCount", receiverIds.size()
        ));
    }

    @GetMapping("/group/{groupId}")
    public ResponseEntity<List<Message>> getGroupHistory(@PathVariable("groupId") Long groupId,
                                                          @RequestParam(name = "page", defaultValue = "0") int page,
                                                          @RequestParam(name = "size", defaultValue = "50") int size) {
        return ResponseEntity.ok(messageService.queryGroupHistory(groupId, page, size));
    }

    @PostMapping("/private")
    public ResponseEntity<?> sendPrivateMessage(@RequestBody Map<String, Object> body) {
        Long senderId = Long.valueOf(body.get("senderId").toString());
        Long receiverId = Long.valueOf(body.get("receiverId").toString());
        String content = (String) body.get("content");

        Message message = messageService.saveMessage(new Message(
                null, senderId, receiverId, 0L, content, System.currentTimeMillis(), false, null));

        Map<String, Object> payload = Map.of(
                "type", "privateMsg",
                "messageId", message.messageId(),
                "senderId", message.senderId(),
                "receiverId", message.receiverId(),
                "content", message.content(),
                "timestamp", message.timestamp()
        );

        pushService.pushSingle(receiverId, message);
        sseMessagePusher.pushToUser(receiverId, payload);
        sseMessagePusher.pushToUser(senderId, payload);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "messageId", message.messageId()
        ));
    }

    @GetMapping("/private/{peerId}")
    public ResponseEntity<List<Message>> getPrivateHistory(@PathVariable("peerId") Long peerId,
                                                            @RequestParam("userId") Long userId,
                                                            @RequestParam(name = "page", defaultValue = "0") int page,
                                                            @RequestParam(name = "size", defaultValue = "50") int size) {
        return ResponseEntity.ok(messageService.queryHistory(userId, peerId, page, size));
    }

}
