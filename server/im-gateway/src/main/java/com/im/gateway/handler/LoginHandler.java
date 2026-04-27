package com.im.gateway.handler;

import com.im.gateway.manager.UserChannelManager;
import com.im.proto.Command;
import com.im.proto.LoginReq;
import com.im.proto.LoginResp;
import com.im.proto.Packet;
import com.im.proto.PacketHeader;
import com.im.push.api.PushService;
import com.im.server.protocol.handler.PacketHandler;
import com.im.session.api.ChannelMetadata;
import com.im.session.api.SessionService;
import com.im.user.api.AuthService;
import com.im.user.api.dto.AuthResult;
import com.im.user.api.dto.LoginRequest;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LoginHandler implements PacketHandler {

    private static final Logger logger = LoggerFactory.getLogger(LoginHandler.class);

    private final AuthService authService;
    private final UserChannelManager userChannelManager;
    private final SessionService sessionService;
    private final PushService pushService;

    public LoginHandler(AuthService authService,
                        UserChannelManager userChannelManager,
                        SessionService sessionService,
                        PushService pushService) {
        this.authService = authService;
        this.userChannelManager = userChannelManager;
        this.sessionService = sessionService;
        this.pushService = pushService;
    }

    @Override
    public void handle(ChannelHandlerContext ctx, Packet packet) {
        LoginReq req = packet.getLoginReq();
        AuthResult result = authService.login(new LoginRequest(req.getUsername(), req.getPassword()));

        LoginResp.Builder respBuilder = LoginResp.newBuilder();
        if (result.success()) {
            respBuilder.setSuccess(true)
                    .setToken(result.token())
                    .setUserId(result.userId())
                    .setNickname(result.nickname());

            Long userId = result.userId();
            userChannelManager.bind(userId, ctx.channel());
            sessionService.bind(userId, ctx.channel().id().asLongText(),
                    new ChannelMetadata(ctx.channel().id().asLongText(),
                            ctx.channel().remoteAddress().toString(), 0));
            logger.info("User {} logged in from {}", userId, ctx.channel().remoteAddress());

            pushService.deliverOfflineMessages(userId);
        } else {
            respBuilder.setSuccess(false).setErrorMessage(result.errorMessage());
            logger.warn("Login failed for {}: {}", req.getUsername(), result.errorMessage());
        }

        Packet respPacket = Packet.newBuilder()
                .setHeader(PacketHeader.newBuilder()
                        .setCmd(Command.CMD_LOGIN_RESP))
                .setLoginResp(respBuilder.build())
                .build();
        ctx.writeAndFlush(respPacket);
    }

    @Override
    public int getCommand() {
        return Command.CMD_LOGIN_REQ.getNumber();
    }
}
