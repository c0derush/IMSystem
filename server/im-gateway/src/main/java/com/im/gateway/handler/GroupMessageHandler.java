package com.im.gateway.handler;

import com.im.gateway.manager.UserChannelManager;
import com.im.group.api.GroupMemberService;
import com.im.group.api.GroupMessageDistributor;
import com.im.message.api.MessageService;
import com.im.message.api.dto.Message;
import com.im.proto.*;
import com.im.push.api.PushService;
import com.im.server.protocol.handler.PacketHandler;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class GroupMessageHandler implements PacketHandler {

    private static final Logger logger = LoggerFactory.getLogger(GroupMessageHandler.class);

    private final MessageService messageService;
    private final PushService pushService;
    private final UserChannelManager userChannelManager;
    private final GroupMemberService groupMemberService;
    private final GroupMessageDistributor groupMessageDistributor;

    public GroupMessageHandler(MessageService messageService,
                               PushService pushService,
                               UserChannelManager userChannelManager,
                               GroupMemberService groupMemberService,
                               GroupMessageDistributor groupMessageDistributor) {
        this.messageService = messageService;
        this.pushService = pushService;
        this.userChannelManager = userChannelManager;
        this.groupMemberService = groupMemberService;
        this.groupMessageDistributor = groupMessageDistributor;
    }

    @Override
    public void handle(ChannelHandlerContext ctx, Packet packet) {
        Long senderId = userChannelManager.getUserId(ctx.channel());
        if (senderId == null) {
            logger.warn("Unauthorized group message from {}", ctx.channel().remoteAddress());
            sendError(ctx, "Not authenticated");
            return;
        }

        TextMessage text = packet.getTextMessage();
        Long groupId = text.getGroupId();
        String clientMessageId = text.getClientMessageId();

        // 校验是否为群成员
        if (!groupMemberService.isMember(groupId, senderId)) {
            logger.warn("User {} is not member of group {}", senderId, groupId);
            sendError(ctx, "Not a group member");
            return;
        }

        Message message = new Message(null, senderId, 0L, groupId,
                text.getContent(), System.currentTimeMillis(), false, clientMessageId);
        Message saved = messageService.saveMessage(message);

        // 获取接收者列表并推送
        List<Long> receiverIds = groupMessageDistributor.distribute(groupId, senderId);
        if (!receiverIds.isEmpty()) {
            pushService.pushGroup(groupId, saved, receiverIds);
        }

        MessageSendResp resp = MessageSendResp.newBuilder()
                .setSuccess(true)
                .setMessageId(saved.messageId())
                .setClientMessageId(saved.clientMessageId())
                .build();
        Packet respPacket = Packet.newBuilder()
                .setHeader(PacketHeader.newBuilder()
                        .setCmd(Command.CMD_GROUP_MSG_RESP))
                .setMessageSendResp(resp)
                .build();
        ctx.writeAndFlush(respPacket);

        logger.debug("Group message {} sent to group {} by {}, receivers={}",
                saved.messageId(), groupId, senderId, receiverIds.size());
    }

    private void sendError(ChannelHandlerContext ctx, String error) {
        ErrorResp errorResp = ErrorResp.newBuilder()
                .setErrorCode(403)
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
        return Command.CMD_GROUP_MSG_REQ.getNumber();
    }
}
