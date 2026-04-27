package com.im.client.codec;

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
 */
public class ClientFrameDecoder extends LengthFieldBasedFrameDecoder {

    public ClientFrameDecoder() {
        // maxFrameLength = 16MB, lengthFieldOffset = 4, lengthFieldLength = 4
        // lengthAdjustment = 0 (完整帧 = bodyLength + 8 字节 header), initialBytesToStrip = 0
        super(16 * 1024 * 1024, 4, 4, 0, 0);
    }
}
