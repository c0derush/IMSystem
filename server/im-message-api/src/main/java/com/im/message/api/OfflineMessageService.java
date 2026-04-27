package com.im.message.api;

import com.im.message.api.dto.Message;

import java.util.List;

public interface OfflineMessageService {

    void storeOffline(Long userId, Message message);

    List<Message> pullOffline(Long userId);

    void removeDelivered(Long userId, List<Long> messageIds);
}
