package com.im.gateway.handler;

import com.im.gateway.manager.UserChannelManager;
import com.im.group.api.GroupMemberService;
import com.im.group.api.GroupService;
import com.im.proto.Command;
import com.im.proto.GroupNotice;
import com.im.proto.GroupOpResp;
import com.im.proto.JoinGroupReq;
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
public class JoinGroupHandler implements PacketHandler {

    private static final Logger logger = LoggerFactory.getLogger(JoinGroupHandler.class);

    private final GroupService groupService;
    private final GroupMemberService groupMemberService;
    private final PushService pushService;
    private final UserChannelManager userChannelManager;

    public JoinGroupHandler(GroupService groupService,
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
            sendGroupOpResp(ctx, 1, false, 0L, "Not authenticated");
            return;
        }

        JoinGroupReq req = packet.getJoinGroupReq();
        Long groupId = req.getGroupId();

        if (!groupService.exists(groupId)) {
            sendGroupOpResp(ctx, 1, false, groupId, "Group not found");
            return;
        }

        try {
            groupMemberService.joinGroup(groupId, senderId);
            sendGroupOpResp(ctx, 1, true, groupId, null);
            logger.info("User {} joined group {}", senderId, groupId);

            List<Long> members = groupMemberService.getMemberIds(groupId);
            List<Long> notifyTargets = members.stream()
                    .filter(id -> !id.equals(senderId))
                    .toList();
            if (!notifyTargets.isEmpty()) {
                GroupNotice notice = GroupNotice.newBuilder()
                        .setGroupId(groupId)
                        .setType(1)
                        .setOperatorId(senderId)
                        .setTargetUserId(senderId)
                        .setTimestamp(System.currentTimeMillis())
                        .build();
                pushService.pushGroupNotice(groupId, notice, notifyTargets);
            }
        } catch (IllegalArgumentException e) {
            logger.warn("Join group failed for user {}: {}", senderId, e.getMessage());
            sendGroupOpResp(ctx, 1, false, groupId, e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error joining group for user {}", senderId, e);
            sendGroupOpResp(ctx, 1, false, groupId, "Internal server error");
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
        return Command.CMD_JOIN_GROUP_REQ.getNumber();
    }
}
