package com.im.push.service;

import com.im.gateway.api.GatewayPushService;
import com.im.message.api.OfflineMessageService;
import com.im.message.api.dto.Message;
import com.im.proto.Command;
import com.im.proto.GroupNotice;
import com.im.proto.Packet;
import com.im.proto.PacketHeader;
import com.im.proto.TextMessage;
import com.im.push.api.PushService;
import com.im.session.api.SessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PushServiceImpl implements PushService {

    private static final Logger logger = LoggerFactory.getLogger(PushServiceImpl.class);

    private final SessionService sessionService;
    private final GatewayPushService gatewayPushService;
    private final OfflineMessageService offlineMessageService;

    public PushServiceImpl(SessionService sessionService,
                           GatewayPushService gatewayPushService,
                           OfflineMessageService offlineMessageService) {
        this.sessionService = sessionService;
        this.gatewayPushService = gatewayPushService;
        this.offlineMessageService = offlineMessageService;
    }

    @Override
    public void pushSingle(Long receiverId, Message message) {
        if (sessionService.isOnline(receiverId)) {
            Packet packet = buildPacket(message);
            gatewayPushService.pushToUser(receiverId, packet);
            logger.debug("Pushed message {} to online user {}", message.messageId(), receiverId);
        } else {
            offlineMessageService.storeOffline(receiverId, message);
            logger.debug("Stored offline message {} for user {}", message.messageId(), receiverId);
        }
    }

    @Override
    public void pushGroup(Long groupId, Message message, List<Long> receiverIds) {
        Packet packet = buildPacket(message);
        for (Long receiverId : receiverIds) {
            if (sessionService.isOnline(receiverId)) {
                gatewayPushService.pushToUser(receiverId, packet);
            } else {
                offlineMessageService.storeOffline(receiverId, message);
            }
        }
    }

    @Override
    public void pushSystemNotification(Long userId, Message message) {
        Packet packet = buildPacket(message);
        if (sessionService.isOnline(userId)) {
            gatewayPushService.pushToUser(userId, packet);
        } else {
            offlineMessageService.storeOffline(userId, message);
        }
    }

    @Override
    public void pushOffline(Long userId, Message message) {
        // MVP: 直接存入离线队列，后续可扩展为调用第三方推送 SDK
        offlineMessageService.storeOffline(userId, message);
    }

    @Override
    public void deliverOfflineMessages(Long userId) {
        try {
            List<Message> offlineMessages = offlineMessageService.pullOffline(userId);
            for (Message msg : offlineMessages) {
                Packet packet = buildPacket(msg);
                gatewayPushService.pushToUser(userId, packet);
            }
            if (!offlineMessages.isEmpty()) {
                offlineMessageService.removeDelivered(userId,
                        offlineMessages.stream().map(Message::messageId).toList());
            }
            logger.debug("Delivered {} offline messages to user {}", offlineMessages.size(), userId);
        } catch (Exception e) {
            logger.error("Failed to deliver offline messages to user {}", userId, e);
        }
    }

    @Override
    public void pushGroupNotice(Long groupId, GroupNotice notice, List<Long> receiverIds) {
        Packet packet = Packet.newBuilder()
                .setHeader(PacketHeader.newBuilder()
                        .setCmd(Command.CMD_GROUP_NOTICE))
                .setGroupNotice(notice)
                .build();

        for (Long receiverId : receiverIds) {
            if (sessionService.isOnline(receiverId)) {
                gatewayPushService.pushToUser(receiverId, packet);
                logger.debug("Pushed group notice to online user {}", receiverId);
            } else {
                logger.debug("User {} offline, group notice dropped", receiverId);
            }
        }
    }

    private Packet buildPacket(Message message) {
        TextMessage text = TextMessage.newBuilder()
                .setMessageId(message.messageId())
                .setSenderId(message.senderId())
                .setReceiverId(message.receiverId())
                .setGroupId(message.groupId() != null ? message.groupId() : 0L)
                .setContent(message.content())
                .setTimestamp(message.timestamp())
                .setClientMessageId(message.clientMessageId() != null ? message.clientMessageId() : "")
                .build();

        return Packet.newBuilder()
                .setHeader(PacketHeader.newBuilder()
                        .setCmd(Command.CMD_SYSTEM_PUSH))
                .setTextMessage(text)
                .build();
    }
}
