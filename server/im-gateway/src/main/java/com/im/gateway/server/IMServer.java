package com.im.gateway.server;

import com.im.server.protocol.codec.IMFrameDecoder;
import com.im.server.protocol.codec.PacketDecoder;
import com.im.server.protocol.codec.PacketEncoder;
import com.im.server.protocol.handler.PacketRouter;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.timeout.IdleStateHandler;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class IMServer {

    private static final Logger logger = LoggerFactory.getLogger(IMServer.class);

    @Value("${netty.tcp.port:8081}")
    private int port;

    @Value("${netty.tcp.reader-idle-seconds:120}")
    private int readerIdleSeconds;

    private final IMChannelHandler imChannelHandler;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;

    public IMServer(IMChannelHandler imChannelHandler) {
        this.imChannelHandler = imChannelHandler;
    }

    @PostConstruct
    public void start() {
        new Thread(this::runServer, "im-server-starter").start();
    }

    private void runServer() {
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();
        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .handler(new LoggingHandler(LogLevel.INFO))
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline()
                                    .addLast(new IMFrameDecoder())
                                    .addLast(new PacketDecoder())
                                    .addLast(new PacketEncoder())
                                    .addLast(new IdleStateHandler(readerIdleSeconds, 0, 0))
                                    .addLast(imChannelHandler);
                        }
                    });

            ChannelFuture future = bootstrap.bind(port).sync();
            logger.info("IM TCP server started on port {}", port);
            future.channel().closeFuture().sync();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("IM server interrupted", e);
        } finally {
            shutdown();
        }
    }

    @PreDestroy
    public void shutdown() {
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
        logger.info("IM server shut down");
    }
}
