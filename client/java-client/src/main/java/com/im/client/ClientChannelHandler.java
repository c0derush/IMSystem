package com.im.client;

import com.im.proto.*;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CopyOnWriteArrayList;

public class ClientChannelHandler extends SimpleChannelInboundHandler<Packet> {

    private static final Logger logger = LoggerFactory.getLogger(ClientChannelHandler.class);

    private final NettyIMClient client;
    private final CopyOnWriteArrayList<MessageListener> messageListeners;
    private final CopyOnWriteArrayList<ConnectionListener> connectionListeners;

    public ClientChannelHandler(NettyIMClient client,
                                CopyOnWriteArrayList<MessageListener> messageListeners,
                                CopyOnWriteArrayList<ConnectionListener> connectionListeners) {
        this.client = client;
        this.messageListeners = messageListeners;
        this.connectionListeners = connectionListeners;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        logger.info("Channel active: {}", ctx.channel().remoteAddress());
        client.onChannelActive(ctx.channel());
        connectionListeners.forEach(ConnectionListener::onConnected);
        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        logger.info("Channel inactive");
        client.onChannelInactive();
        connectionListeners.forEach(ConnectionListener::onDisconnected);
        super.channelInactive(ctx);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Packet packet) throws Exception {
        Command cmd = packet.getHeader().getCmd();
        logger.info("Received packet: cmd={}", cmd);

        switch (cmd) {
            case CMD_HEARTBEAT_RESP -> handleHeartbeatResp(packet);
            case CMD_LOGIN_RESP -> handleLoginResp(packet);
            case CMD_REGISTER_RESP -> handleRegisterResp(packet);
            case CMD_SINGLE_MSG_RESP, CMD_GROUP_MSG_RESP -> handleMessageSendResp(packet);
            case CMD_MESSAGE_ACK -> handleMessageAck(packet);
            case CMD_SYSTEM_PUSH -> handleSystemPush(packet);
            case CMD_CREATE_GROUP_RESP -> handleCreateGroupResp(packet);
            case CMD_GROUP_OP_RESP -> handleGroupOpResp(packet);
            case CMD_GROUP_NOTICE -> handleGroupNotice(packet);
            case CMD_ERROR_RESP -> handleError(packet);
            default -> {
                // 单聊/群聊文本消息走 textMessage 字段
                if (packet.hasTextMessage()) {
                    messageListeners.forEach(l -> l.onMessageReceived(packet.getTextMessage()));
                } else {
                    logger.warn("Unhandled command: {}", cmd);
                }
            }
        }
    }

    private void handleHeartbeatResp(Packet packet) {
        logger.debug("Heartbeat response received");
    }

    private void handleLoginResp(Packet packet) {
        LoginResp resp = packet.getLoginResp();
        if (resp.getSuccess()) {
            client.onLoginSuccess(resp.getUserId(), resp.getToken());
            connectionListeners.forEach(l -> l.onLoginSuccess(resp.getUserId(), resp.getNickname()));
        } else {
            connectionListeners.forEach(l -> l.onLoginFailed(resp.getErrorMessage()));
        }
    }

    private void handleRegisterResp(Packet packet) {
        RegisterResp resp = packet.getRegisterResp();
        if (resp.getSuccess()) {
            logger.info("Register success: userId={}", resp.getUserId());
            // 注册成功后自动走登录流程，或通知监听器
            connectionListeners.forEach(l -> l.onLoginSuccess(resp.getUserId(), resp.getNickname()));
        } else {
            logger.warn("Register failed: {}", resp.getErrorMessage());
            connectionListeners.forEach(l -> l.onLoginFailed(resp.getErrorMessage()));
        }
    }

    private void handleMessageSendResp(Packet packet) {
        if (packet.hasMessageSendResp()) {
            MessageSendResp resp = packet.getMessageSendResp();
            client.onMessageAck(resp.getClientMessageId(), resp.getSuccess() ? 0 : 1);
            messageListeners.forEach(l -> l.onMessageAck(resp.getClientMessageId(), resp.getSuccess() ? 0 : 1));
        }
    }

    private void handleMessageAck(Packet packet) {
        if (packet.hasMessageAck()) {
            MessageAck ack = packet.getMessageAck();
            String msgId = String.valueOf(ack.getMessageId());
            client.onMessageAck(msgId, ack.getStatus());
            messageListeners.forEach(l -> l.onMessageAck(msgId, ack.getStatus()));
        }
    }

    private void handleSystemPush(Packet packet) {
        if (packet.hasTextMessage()) {
            messageListeners.forEach(l -> l.onMessageReceived(packet.getTextMessage()));
        } else {
            logger.info("System push received (no text message)");
        }
    }

    private void handleError(Packet packet) {
        if (packet.hasErrorResp()) {
            ErrorResp err = packet.getErrorResp();
            logger.error("Server error: code={}, msg={}", err.getErrorCode(), err.getErrorMessage());
        }
    }

    private void handleCreateGroupResp(Packet packet) {
        if (packet.hasCreateGroupResp()) {
            CreateGroupResp resp = packet.getCreateGroupResp();
            if (!resp.getSuccess()) {
                logger.warn("Create group failed: {}", resp.getErrorMessage());
            } else {
                logger.info("Create group success: groupId={}, name={}", resp.getGroupId(), resp.getName());
            }
            client.getGroupOperationListeners().forEach(l -> l.onCreateGroupResult(resp));
        }
    }

    private void handleGroupOpResp(Packet packet) {
        if (packet.hasGroupOpResp()) {
            GroupOpResp resp = packet.getGroupOpResp();
            if (!resp.getSuccess()) {
                logger.warn("Group op failed: type={}, msg={}", resp.getOpType(), resp.getErrorMessage());
            } else {
                logger.info("Group op success: type={}, groupId={}", resp.getOpType(), resp.getGroupId());
            }
            client.getGroupOperationListeners().forEach(l -> l.onGroupOpResult(resp));
        }
    }

    private void handleGroupNotice(Packet packet) {
        if (packet.hasGroupNotice()) {
            GroupNotice notice = packet.getGroupNotice();
            logger.info("Group notice: groupId={}, type={}, operatorId={}, targetUserId={}",
                    notice.getGroupId(), notice.getType(), notice.getOperatorId(), notice.getTargetUserId());
            client.getGroupOperationListeners().forEach(l -> l.onGroupNoticeReceived(notice));
        }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent event) {
            if (event.state() == IdleState.WRITER_IDLE) {
                client.sendHeartbeat();
            }
        }
        super.userEventTriggered(ctx, evt);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error("Channel exception", cause);
        ctx.close();
    }
}
