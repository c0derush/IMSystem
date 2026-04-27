package com.im.server.protocol.handler;

import com.im.proto.Packet;
import io.netty.channel.ChannelHandlerContext;

/**
 * 业务层数据包处理器接口。
 *
 * <p>每个命令码对应一个实现类，由 {@link PacketRouter} 负责调度。
 */
public interface PacketHandler {

    /**
     * 处理数据包。
     *
     * @param ctx 当前连接的 Channel 上下文
     * @param packet 解码后的 Protobuf Packet
     */
    void handle(ChannelHandlerContext ctx, Packet packet);

    /**
     * @return 本处理器负责的命令码
     */
    int getCommand();
}
