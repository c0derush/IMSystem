package com.im.message.api.dto;

public record Message(
        Long messageId,
        Long senderId,
        Long receiverId,
        Long groupId,
        String content,
        Long timestamp,
        boolean read,
        String clientMessageId
) {
    public Message {
        if (clientMessageId == null) {
            clientMessageId = "";
        }
    }

    public Message(Long messageId, Long senderId, Long receiverId, Long groupId,
                   String content, Long timestamp, boolean read) {
        this(messageId, senderId, receiverId, groupId, content, timestamp, read, "");
    }
}
