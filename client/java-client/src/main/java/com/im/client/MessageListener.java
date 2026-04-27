package com.im.client;

import com.im.proto.TextMessage;

/**
 * 消息监听回调。
 */
public interface MessageListener {

    /**
     * 收到单聊或群聊文本消息。
     */
    void onMessageReceived(TextMessage message);

    /**
     * 收到消息回执（ACK）。
     *
     * @param messageId 服务端分配的消息 ID
     * @param status    状态码：0 成功，其他为错误码
     */
    void onMessageAck(String messageId, int status);
}
