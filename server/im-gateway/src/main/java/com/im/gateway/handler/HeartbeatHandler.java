package com.im.gateway.handler;

import com.im.proto.*;
import com.im.server.protocol.handler.PacketHandler;
import io.netty.channel.ChannelHandlerContext;
import org.springframework.stereotype.Component;

@Component
public class HeartbeatHandler implements PacketHandler {

    @Override
    public void handle(ChannelHandlerContext ctx, Packet packet) {
        HeartbeatReq req = packet.getHeartbeatReq();
        HeartbeatResp resp = HeartbeatResp.newBuilder()
                .setTimestamp(System.currentTimeMillis())
                .build();

        Packet respPacket = Packet.newBuilder()
                .setHeader(PacketHeader.newBuilder()
                        .setCmd(Command.CMD_HEARTBEAT_RESP))
                .setHeartbeatResp(resp)
                .build();
        ctx.writeAndFlush(respPacket);
    }

    @Override
    public int getCommand() {
        return Command.CMD_HEARTBEAT_REQ.getNumber();
    }
}
