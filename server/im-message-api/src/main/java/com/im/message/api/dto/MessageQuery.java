package com.im.message.api.dto;

public record MessageQuery(Long userId, Long peerId, int page, int size) {
}
