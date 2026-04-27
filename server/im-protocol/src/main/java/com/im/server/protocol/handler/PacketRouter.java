package com.im.server.protocol.handler;

import com.im.proto.Command;
import com.im.proto.Packet;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PacketRouter {

    private static final Logger logger = LoggerFactory.getLogger(PacketRouter.class);
    private final Map<Integer, PacketHandler> handlerMap = new ConcurrentHashMap<>();

    public void register(PacketHandler handler) {
        handlerMap.put(handler.getCommand(), handler);
    }

    public void route(ChannelHandlerContext ctx, Packet packet) {
        int cmd = packet.getHeader().getCmd().getNumber();
        PacketHandler handler = handlerMap.get(cmd);
        if (handler != null) {
            try {
                handler.handle(ctx, packet);
            } catch (Exception e) {
                logger.error("Handler exception for cmd={}", cmd, e);
            }
        } else {
            logger.warn("No handler registered for cmd={}", cmd);
        }
    }
}
