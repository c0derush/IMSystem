package com.im.gateway.manager;

import io.netty.channel.Channel;
import io.netty.channel.ChannelId;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class UserChannelManager {

    private final ConcurrentHashMap<Long, Channel> userChannelMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<ChannelId, Long> channelUserMap = new ConcurrentHashMap<>();

    public void bind(Long userId, Channel channel) {
        Channel existing = userChannelMap.get(userId);
        if (existing != null && existing.isActive() && existing != channel) {
            existing.close();
        }
        userChannelMap.put(userId, channel);
        channelUserMap.put(channel.id(), userId);
    }

    public void unbind(Channel channel) {
        Long userId = channelUserMap.remove(channel.id());
        if (userId != null) {
            userChannelMap.remove(userId, channel);
        }
    }

    public Channel getChannel(Long userId) {
        return userChannelMap.get(userId);
    }

    public Long getUserId(Channel channel) {
        return channelUserMap.get(channel.id());
    }

    public boolean isOnline(Long userId) {
        Channel channel = userChannelMap.get(userId);
        return channel != null && channel.isActive();
    }

    public Collection<Channel> getAllChannels() {
        return userChannelMap.values();
    }
}
