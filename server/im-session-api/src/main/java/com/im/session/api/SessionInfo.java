package com.im.session.api;

public record SessionInfo(String sessionId, Long userId, long bindTime) {
}
