package com.im.gateway.handler;

import com.im.gateway.manager.UserChannelManager;
import com.im.group.api.GroupMemberService;
import com.im.group.api.GroupService;
import com.im.proto.Command;
import com.im.proto.DissolveGroupReq;
import com.im.proto.GroupNotice;
import com.im.proto.GroupOpResp;
import com.im.proto.Packet;
import com.im.proto.PacketHeader;
import com.im.push.api.PushService;
import com.im.server.protocol.handler.PacketHandler;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DissolveGroupHandler implements PacketHandler {

    private static final Logger logger = LoggerFactory.getLogger(DissolveGroupHandler.class);

    private final GroupService groupService;
    private final GroupMemberService groupMemberService;
    private final PushService pushService;
    private final UserChannelManager userChannelManager;

    public DissolveGroupHandler(GroupService groupService,
                                GroupMemberService groupMemberService,
                                PushService pushService,
                                UserChannelManager userChannelManager) {
        this.groupService = groupService;
        this.groupMemberService = groupMemberService;
        this.pushService = pushService;
        this.userChannelManager = userChannelManager;
    }

    @Override
    public void handle(ChannelHandlerContext ctx, Packet packet) {
        Long senderId = userChannelManager.getUserId(ctx.channel());
        if (senderId == null) {
            sendGroupOpResp(ctx, 4, false, 0L, "Not authenticated");
            return;
        }

        DissolveGroupReq req = packet.getDissolveGroupReq();
        Long groupId = req.getGroupId();

        try {
            List<Long> membersBeforeDissolve = groupMemberService.getMemberIds(groupId);
            groupService.dissolveGroup(groupId, senderId);
            sendGroupOpResp(ctx, 4, true, groupId, null);
            logger.info("User {} dissolved group {}", senderId, groupId);

            if (!membersBeforeDissolve.isEmpty()) {
                GroupNotice notice = GroupNotice.newBuilder()
                        .setGroupId(groupId)
                        .setType(4)
                        .setOperatorId(senderId)
                        .setTimestamp(System.currentTimeMillis())
                        .build();
                pushService.pushGroupNotice(groupId, notice, membersBeforeDissolve);
            }
        } catch (IllegalArgumentException e) {
            logger.warn("Dissolve group failed for user {}: {}", senderId, e.getMessage());
            sendGroupOpResp(ctx, 4, false, groupId, e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error dissolving group for user {}", senderId, e);
            sendGroupOpResp(ctx, 4, false, groupId, "Internal server error");
        }
    }

    private void sendGroupOpResp(ChannelHandlerContext ctx, int opType, boolean success,
                                 long groupId, String errorMessage) {
        GroupOpResp.Builder builder = GroupOpResp.newBuilder()
                .setOpType(opType)
                .setSuccess(success)
                .setGroupId(groupId);
        if (errorMessage != null) {
            builder.setErrorMessage(errorMessage);
        }

        Packet respPacket = Packet.newBuilder()
                .setHeader(PacketHeader.newBuilder().setCmd(Command.CMD_GROUP_OP_RESP))
                .setGroupOpResp(builder.build())
                .build();
        ctx.writeAndFlush(respPacket);
    }

    @Override
    public int getCommand() {
        return Command.CMD_DISSOLVE_GROUP_REQ.getNumber();
    }
}
