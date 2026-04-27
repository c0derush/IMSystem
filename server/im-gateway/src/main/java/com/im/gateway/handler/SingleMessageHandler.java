package com.im.gateway.handler;

import com.im.gateway.manager.UserChannelManager;
import com.im.message.api.MessageService;
import com.im.message.api.dto.Message;
import com.im.proto.*;
import com.im.push.api.PushService;
import com.im.server.protocol.handler.PacketHandler;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class SingleMessageHandler implements PacketHandler {

    private static final Logger logger = LoggerFactory.getLogger(SingleMessageHandler.class);

    private final MessageService messageService;
    private final PushService pushService;
    private final UserChannelManager userChannelManager;

    public SingleMessageHandler(MessageService messageService,
                                PushService pushService,
                                UserChannelManager userChannelManager) {
        this.messageService = messageService;
        this.pushService = pushService;
        this.userChannelManager = userChannelManager;
    }

    @Override
    public void handle(ChannelHandlerContext ctx, Packet packet) {
        Long senderId = userChannelManager.getUserId(ctx.channel());
        if (senderId == null) {
            logger.warn("Unauthorized message from {}", ctx.channel().remoteAddress());
            sendError(ctx, "Not authenticated");
            return;
        }

        TextMessage text = packet.getTextMessage();
        Long receiverId = text.getReceiverId();
        String clientMessageId = text.getClientMessageId();

        Message message = new Message(null, senderId, receiverId, 0L,
                text.getContent(), System.currentTimeMillis(), false, clientMessageId);
        Message saved = messageService.saveMessage(message);

        pushService.pushSingle(receiverId, saved);

        MessageSendResp resp = MessageSendResp.newBuilder()
                .setSuccess(true)
                .setMessageId(saved.messageId())
                .setClientMessageId(saved.clientMessageId())
                .build();
        Packet respPacket = Packet.newBuilder()
                .setHeader(PacketHeader.newBuilder()
                        .setCmd(Command.CMD_SINGLE_MSG_RESP))
                .setMessageSendResp(resp)
                .build();
        ctx.writeAndFlush(respPacket);

        logger.debug("Message {} sent from {} to {}", saved.messageId(), senderId, receiverId);
    }

    private void sendError(ChannelHandlerContext ctx, String error) {
        ErrorResp errorResp = ErrorResp.newBuilder()
                .setErrorCode(401)
                .setErrorMessage(error)
                .build();
        Packet packet = Packet.newBuilder()
                .setHeader(PacketHeader.newBuilder()
                        .setCmd(Command.CMD_ERROR_RESP))
                .setErrorResp(errorResp)
                .build();
        ctx.writeAndFlush(packet);
    }

    @Override
    public int getCommand() {
        return Command.CMD_SINGLE_MSG_REQ.getNumber();
    }
}
