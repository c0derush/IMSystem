package com.im.gateway.service;

import com.im.gateway.api.GatewayPushService;
import com.im.gateway.manager.UserChannelManager;
import com.im.proto.Packet;
import io.netty.channel.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class GatewayPushServiceImpl implements GatewayPushService {

    private static final Logger logger = LoggerFactory.getLogger(GatewayPushServiceImpl.class);
    private final UserChannelManager userChannelManager;

    public GatewayPushServiceImpl(UserChannelManager userChannelManager) {
        this.userChannelManager = userChannelManager;
    }

    @Override
    public void pushToUser(Long userId, Packet packet) {
        Channel channel = userChannelManager.getChannel(userId);
        if (channel != null && channel.isActive()) {
            channel.writeAndFlush(packet);
            logger.debug("Pushed packet to user {}", userId);
        } else {
            logger.debug("User {} is not online, push dropped", userId);
        }
    }

    @Override
    public void pushToGroup(Long groupId, Packet packet) {
        // 群聊功能暂不实现
        logger.debug("pushToGroup stub called for group {}", groupId);
    }

    @Override
    public void broadcast(Packet packet) {
        for (Channel channel : userChannelManager.getAllChannels()) {
            if (channel.isActive()) {
                channel.writeAndFlush(packet);
            }
        }
        logger.debug("Broadcast packet to all online users");
    }

    @Override
    public boolean isOnline(Long userId) {
        return userChannelManager.isOnline(userId);
    }

    @Override
    public void disconnect(Long userId) {
        Channel channel = userChannelManager.getChannel(userId);
        if (channel != null) {
            channel.close();
        }
    }
}
