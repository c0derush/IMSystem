package com.im.server.protocol.codec;

import io.netty.handler.codec.LengthFieldBasedFrameDecoder;

/**
 * TCP 粘包拆包解码器。
 *
 * <p>协议帧格式（固定 8 字节头部）：
 * <pre>
 *  +---------+---------+------+------------+---------+
 *  |  magic  | version | cmd  | bodyLength |  body   |
 *  | 2 bytes | 1 byte  |1 byte|  4 bytes   | N bytes |
 *  +---------+---------+------+------------+---------+
 * </pre>
 *
 * <p>lengthFieldOffset = 4（magic + version + cmd）
 * <br>lengthFieldLength = 4（bodyLength 字段长度）
 * <br>lengthAdjustment = 0（bodyLength 只表示 body，完整帧 = bodyLength + 8 字节 header）
 * <br>initialBytesToStrip = 0（保留完整帧，交给 PacketDecoder 解析头部）
 */
public class IMFrameDecoder extends LengthFieldBasedFrameDecoder {

    public IMFrameDecoder() {
        // maxFrameLength = 16MB, lengthFieldOffset = 4, lengthFieldLength = 4
        // lengthAdjustment = 0, initialBytesToStrip = 0
        super(16 * 1024 * 1024, 4, 4, 0, 0);
    }
}
