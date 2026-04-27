package com.im.gateway.controller;

import com.im.gateway.sse.SseMessagePusher;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/sse")
public class SseController {

    private final SseMessagePusher sseMessagePusher;

    public SseController(SseMessagePusher sseMessagePusher) {
        this.sseMessagePusher = sseMessagePusher;
    }

    @GetMapping("/subscribe")
    public SseEmitter subscribe(@RequestParam("userId") Long userId) {
        return sseMessagePusher.subscribe(userId);
    }
}
