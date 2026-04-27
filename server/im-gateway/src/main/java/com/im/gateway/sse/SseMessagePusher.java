package com.im.gateway.sse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SseMessagePusher {

    private static final Logger logger = LoggerFactory.getLogger(SseMessagePusher.class);

    private final Map<Long, SseEmitter> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(Long userId) {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.put(userId, emitter);

        emitter.onCompletion(() -> emitters.remove(userId));
        emitter.onTimeout(() -> emitters.remove(userId));
        emitter.onError(e -> emitters.remove(userId));

        logger.debug("SSE subscribed for user {}", userId);
        return emitter;
    }

    public void pushToUser(Long userId, Object data) {
        SseEmitter emitter = emitters.get(userId);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event().name("message").data(data));
            } catch (IOException e) {
                logger.warn("Failed to push SSE to user {}, removing emitter", userId);
                emitters.remove(userId);
            }
        }
    }

    public void pushToUsers(java.util.List<Long> userIds, Object data) {
        for (Long userId : userIds) {
            pushToUser(userId, data);
        }
    }
}
