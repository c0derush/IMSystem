package com.im.gateway.handler;

import com.im.gateway.manager.UserChannelManager;
import com.im.message.api.MessageService;
import com.im.proto.Command;
import com.im.proto.MessageAck;
import com.im.proto.Packet;
import com.im.server.protocol.handler.PacketHandler;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class MessageAckHandler implements PacketHandler {

    private static final Logger logger = LoggerFactory.getLogger(MessageAckHandler.class);

    private static final int STATUS_DELIVERED = 1;
    private static final int STATUS_READ = 2;

    private final MessageService messageService;
    private final UserChannelManager userChannelManager;

    public MessageAckHandler(MessageService messageService, UserChannelManager userChannelManager) {
        this.messageService = messageService;
        this.userChannelManager = userChannelManager;
    }

    @Override
    public void handle(ChannelHandlerContext ctx, Packet packet) {
        Long userId = userChannelManager.getUserId(ctx.channel());
        if (userId == null) {
            logger.warn("Unauthorized ack from {}", ctx.channel().remoteAddress());
            return;
        }

        MessageAck ack = packet.getMessageAck();
        int status = ack.getStatus();
        long messageId = ack.getMessageId();

        if (status == STATUS_READ) {
            messageService.markAsRead(userId, messageId);
            logger.debug("Message {} marked as read by user {}", messageId, userId);
        } else if (status == STATUS_DELIVERED) {
            // MVP: 已送达状态暂不落盘，后续可扩展为更新消息状态表
            logger.debug("Message {} delivered to user {}", messageId, userId);
        }
    }

    @Override
    public int getCommand() {
        return Command.CMD_MESSAGE_ACK.getNumber();
    }
}
