package com.im.gateway.api;

import com.im.proto.Packet;

public interface GatewayPushService {

    void pushToUser(Long userId, Packet packet);

    void pushToGroup(Long groupId, Packet packet);

    void broadcast(Packet packet);

    boolean isOnline(Long userId);

    void disconnect(Long userId);
}
