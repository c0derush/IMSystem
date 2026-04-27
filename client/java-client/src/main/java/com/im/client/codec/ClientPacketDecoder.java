package com.im.client.codec;

import com.google.protobuf.InvalidProtocolBufferException;
import com.im.proto.Packet;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class ClientPacketDecoder extends ByteToMessageDecoder {

    private static final Logger logger = LoggerFactory.getLogger(ClientPacketDecoder.class);
    private static final int MAGIC = 0xCAFE;

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        if (in.readableBytes() < 8) {
            return;
        }

        in.markReaderIndex();
        int magic = in.readUnsignedShort();
        if (magic != MAGIC) {
            logger.warn("Invalid magic: 0x{} from {}", Integer.toHexString(magic), ctx.channel().remoteAddress());
            ctx.close();
            return;
        }

        int version = in.readUnsignedByte();
        int cmd = in.readUnsignedByte();
        int bodyLength = in.readInt();

        if (in.readableBytes() < bodyLength) {
            in.resetReaderIndex();
            return;
        }

        byte[] body = new byte[bodyLength];
        in.readBytes(body);

        try {
            Packet packet = Packet.parseFrom(body);
            out.add(packet);
        } catch (InvalidProtocolBufferException e) {
            logger.warn("Failed to parse protobuf packet: {}", e.getMessage());
        }
    }
}
