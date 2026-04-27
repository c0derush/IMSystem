package com.im.message.api;

import com.im.message.api.dto.Message;
import com.im.message.api.dto.MessageQuery;

import java.util.List;

public interface MessageService {

    Message saveMessage(Message message);

    List<Message> queryHistory(Long userId, Long peerId, int page, int size);

    List<Message> queryGroupHistory(Long groupId, int page, int size);

    void markAsRead(Long userId, Long messageId);

    int getUnreadCount(Long userId);

    int getUnreadCount(Long userId, Long peerId);
}
