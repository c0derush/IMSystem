package com.im.gateway.server;

import com.im.gateway.manager.UserChannelManager;
import com.im.proto.Packet;
import com.im.server.protocol.handler.PacketRouter;
import com.im.session.api.SessionService;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
@ChannelHandler.Sharable
public class IMChannelHandler extends SimpleChannelInboundHandler<Packet> {

    private static final Logger logger = LoggerFactory.getLogger(IMChannelHandler.class);

    private final PacketRouter packetRouter;
    private final UserChannelManager userChannelManager;
    private final SessionService sessionService;

    public IMChannelHandler(PacketRouter packetRouter,
                            UserChannelManager userChannelManager,
                            SessionService sessionService) {
        this.packetRouter = packetRouter;
        this.userChannelManager = userChannelManager;
        this.sessionService = sessionService;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        logger.info("Channel active: {}", ctx.channel().remoteAddress());
        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        Long userId = userChannelManager.getUserId(ctx.channel());
        if (userId != null) {
            logger.info("User {} disconnected", userId);
        }
        sessionService.unbind(ctx.channel().id().asLongText());
        userChannelManager.unbind(ctx.channel());
        super.channelInactive(ctx);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Packet packet) throws Exception {
        packetRouter.route(ctx, packet);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent event) {
            if (event.state() == IdleState.READER_IDLE) {
                logger.info("Closing idle channel: {}", ctx.channel().remoteAddress());
                ctx.close();
            }
        }
        super.userEventTriggered(ctx, evt);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error("Channel exception: {}", ctx.channel().remoteAddress(), cause);
        ctx.close();
    }
}
