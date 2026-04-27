package com.im.push.api;

import com.im.message.api.dto.Message;

import java.util.List;

public interface PushService {

    /**
     * 推送单聊消息
     */
    void pushSingle(Long receiverId, Message message);

    /**
     * 推送群消息（传入已决策好的接收者列表）
     */
    void pushGroup(Long groupId, Message message, List<Long> receiverIds);

    /**
     * 推送系统通知给指定用户
     */
    void pushSystemNotification(Long userId, Message message);

    /**
     * 发送离线推送通知（APP 被杀死时走厂商通道）
     */
    void pushOffline(Long userId, Message message);

    /**
     * 用户上线时，将堆积的离线消息全部推送给该用户
     */
    void deliverOfflineMessages(Long userId);

    /**
     * 推送群成员变更通知（GroupNotice）给指定群成员。
     * <p>MVP 阶段：仅推送给在线成员，离线成员不存储。
     *
     * @param groupId     群 ID
     * @param notice      群通知 Protobuf 对象
     * @param receiverIds 接收者 ID 列表
     */
    void pushGroupNotice(Long groupId, com.im.proto.GroupNotice notice, List<Long> receiverIds);
}
