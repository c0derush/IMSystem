package com.im.gateway.handler;

import com.im.gateway.manager.UserChannelManager;
import com.im.proto.Command;
import com.im.proto.Packet;
import com.im.proto.PacketHeader;
import com.im.proto.RegisterReq;
import com.im.proto.RegisterResp;
import com.im.server.protocol.handler.PacketHandler;
import com.im.user.api.AuthService;
import com.im.user.api.dto.AuthResult;
import com.im.user.api.dto.RegisterRequest;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class RegisterHandler implements PacketHandler {

    private static final Logger logger = LoggerFactory.getLogger(RegisterHandler.class);

    private final AuthService authService;
    private final UserChannelManager userChannelManager;

    public RegisterHandler(AuthService authService, UserChannelManager userChannelManager) {
        this.authService = authService;
        this.userChannelManager = userChannelManager;
    }

    @Override
    public void handle(ChannelHandlerContext ctx, Packet packet) {
        RegisterReq req = packet.getRegisterReq();
        logger.info("Register request received: username={}, nickname={}", req.getUsername(), req.getNickname());

        try {
            AuthResult result = authService.register(
                    new RegisterRequest(req.getUsername(), req.getPassword(), req.getNickname()));
            logger.info("AuthService.register returned: success={}, userId={}", result.success(), result.userId());

            RegisterResp.Builder respBuilder = RegisterResp.newBuilder();
            if (result.success()) {
                respBuilder.setSuccess(true)
                        .setToken(result.token())
                        .setUserId(result.userId())
                        .setNickname(result.nickname());
                logger.info("User registered: {}", req.getUsername());
            } else {
                respBuilder.setSuccess(false).setErrorMessage(result.errorMessage());
                logger.warn("Registration failed for {}: {}", req.getUsername(), result.errorMessage());
            }

            Packet respPacket = Packet.newBuilder()
                    .setHeader(PacketHeader.newBuilder()
                            .setCmd(Command.CMD_REGISTER_RESP))
                    .setRegisterResp(respBuilder.build())
                    .build();
            ctx.writeAndFlush(respPacket);
            logger.info("Register response sent to {}", ctx.channel().remoteAddress());
        } catch (Exception e) {
            logger.error("Exception during registration for {}", req.getUsername(), e);
            RegisterResp resp = RegisterResp.newBuilder()
                    .setSuccess(false)
                    .setErrorMessage("Internal error: " + e.getMessage())
                    .build();
            Packet respPacket = Packet.newBuilder()
                    .setHeader(PacketHeader.newBuilder().setCmd(Command.CMD_REGISTER_RESP))
                    .setRegisterResp(resp)
                    .build();
            ctx.writeAndFlush(respPacket);
        }
    }

    @Override
    public int getCommand() {
        return Command.CMD_REGISTER_REQ.getNumber();
    }
}
