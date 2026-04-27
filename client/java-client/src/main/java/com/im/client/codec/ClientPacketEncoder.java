package com.im.client.codec;

import com.im.proto.Packet;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

public class ClientPacketEncoder extends MessageToByteEncoder<Packet> {

    private static final int MAGIC = 0xCAFE;
    private static final int VERSION = 1;

    @Override
    protected void encode(ChannelHandlerContext ctx, Packet msg, ByteBuf out) throws Exception {
        byte[] body = msg.toByteArray();
        int cmd = msg.getHeader().getCmd().getNumber();

        out.writeShort(MAGIC);
        out.writeByte(VERSION);
        out.writeByte(cmd);
        out.writeInt(body.length);
        out.writeBytes(body);
    }
}
