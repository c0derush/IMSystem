package com.im.gateway.handler;

import com.im.gateway.manager.UserChannelManager;
import com.im.group.api.GroupInfo;
import com.im.group.api.GroupService;
import com.im.proto.Command;
import com.im.proto.CreateGroupReq;
import com.im.proto.CreateGroupResp;
import com.im.proto.Packet;
import com.im.proto.PacketHeader;
import com.im.server.protocol.handler.PacketHandler;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class CreateGroupHandler implements PacketHandler {

    private static final Logger logger = LoggerFactory.getLogger(CreateGroupHandler.class);

    private final GroupService groupService;
    private final UserChannelManager userChannelManager;

    public CreateGroupHandler(GroupService groupService,
                              UserChannelManager userChannelManager) {
        this.groupService = groupService;
        this.userChannelManager = userChannelManager;
    }

    @Override
    public void handle(ChannelHandlerContext ctx, Packet packet) {
        Long senderId = userChannelManager.getUserId(ctx.channel());
        if (senderId == null) {
            sendCreateGroupResp(ctx, false, 0L, null, 0L, 0, "Not authenticated");
            return;
        }

        try {
            CreateGroupReq req = packet.getCreateGroupReq();
            GroupInfo info = groupService.createGroup(senderId, req.getName());
            sendCreateGroupResp(ctx, true, info.groupId(), info.name(), info.creatorId(), info.memberCount(), null);
            logger.info("User {} created group {}: {}", senderId, info.groupId(), info.name());
        } catch (IllegalArgumentException e) {
            logger.warn("Create group failed for user {}: {}", senderId, e.getMessage());
            sendCreateGroupResp(ctx, false, 0L, null, 0L, 0, e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error creating group for user {}", senderId, e);
            sendCreateGroupResp(ctx, false, 0L, null, 0L, 0, "Internal server error");
        }
    }

    private void sendCreateGroupResp(ChannelHandlerContext ctx, boolean success, long groupId,
                                     String name, long creatorId, int memberCount, String errorMessage) {
        CreateGroupResp.Builder builder = CreateGroupResp.newBuilder()
                .setSuccess(success)
                .setGroupId(groupId)
                .setName(name != null ? name : "")
                .setCreatorId(creatorId)
                .setMemberCount(memberCount);
        if (errorMessage != null) {
            builder.setErrorMessage(errorMessage);
        }

        Packet respPacket = Packet.newBuilder()
                .setHeader(PacketHeader.newBuilder().setCmd(Command.CMD_CREATE_GROUP_RESP))
                .setCreateGroupResp(builder.build())
                .build();
        ctx.writeAndFlush(respPacket);
    }

    @Override
    public int getCommand() {
        return Command.CMD_CREATE_GROUP_REQ.getNumber();
    }
}
