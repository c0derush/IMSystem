package com.im.gateway.handler;

import com.im.gateway.manager.UserChannelManager;
import com.im.group.api.GroupMemberService;
import com.im.proto.Command;
import com.im.proto.GroupNotice;
import com.im.proto.GroupOpResp;
import com.im.proto.KickMemberReq;
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
public class KickMemberHandler implements PacketHandler {

    private static final Logger logger = LoggerFactory.getLogger(KickMemberHandler.class);

    private final GroupMemberService groupMemberService;
    private final PushService pushService;
    private final UserChannelManager userChannelManager;

    public KickMemberHandler(GroupMemberService groupMemberService,
                             PushService pushService,
                             UserChannelManager userChannelManager) {
        this.groupMemberService = groupMemberService;
        this.pushService = pushService;
        this.userChannelManager = userChannelManager;
    }

    @Override
    public void handle(ChannelHandlerContext ctx, Packet packet) {
        Long senderId = userChannelManager.getUserId(ctx.channel());
        if (senderId == null) {
            sendGroupOpResp(ctx, 3, false, 0L, "Not authenticated");
            return;
        }

        KickMemberReq req = packet.getKickMemberReq();
        Long groupId = req.getGroupId();
        Long targetUserId = req.getTargetUserId();

        try {
            groupMemberService.kickMember(groupId, senderId, targetUserId);
            sendGroupOpResp(ctx, 3, true, groupId, null);
            logger.info("User {} kicked user {} from group {}", senderId, targetUserId, groupId);

            List<Long> members = groupMemberService.getMemberIds(groupId);
            if (!members.isEmpty()) {
                GroupNotice notice = GroupNotice.newBuilder()
                        .setGroupId(groupId)
                        .setType(3)
                        .setOperatorId(senderId)
                        .setTargetUserId(targetUserId)
                        .setTimestamp(System.currentTimeMillis())
                        .build();
                pushService.pushGroupNotice(groupId, notice, members);
            }
        } catch (IllegalArgumentException e) {
            logger.warn("Kick member failed for user {}: {}", senderId, e.getMessage());
            sendGroupOpResp(ctx, 3, false, groupId, e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error kicking member for user {}", senderId, e);
            sendGroupOpResp(ctx, 3, false, groupId, "Internal server error");
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
        return Command.CMD_KICK_MEMBER_REQ.getNumber();
    }
}
