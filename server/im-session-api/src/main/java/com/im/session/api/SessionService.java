package com.im.session.api;

import java.util.List;

public interface SessionService {

    void bind(Long userId, String sessionId, ChannelMetadata channel);

    void unbind(String sessionId);

    List<SessionInfo> getSessions(Long userId);

    boolean isOnline(Long userId);

    long getOnlineCount();
}
