package com.im.group.api;

/**
 * 群信息 DTO
 */
public record GroupInfo(Long groupId, String name, Long creatorId, int memberCount, Long createdAt) {
}
