package com.im.session.service;

import com.im.session.api.ChannelMetadata;
import com.im.session.api.SessionInfo;
import com.im.session.api.SessionService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SessionServiceImpl implements SessionService {

    // userId -> list of sessionIds
    private final Map<Long, List<String>> userSessions = new ConcurrentHashMap<>();
    // sessionId -> SessionInfo
    private final Map<String, SessionInfo> sessionMap = new ConcurrentHashMap<>();
    // sessionId -> ChannelMetadata
    private final Map<String, ChannelMetadata> channelMap = new ConcurrentHashMap<>();

    @Override
    public void bind(Long userId, String sessionId, ChannelMetadata channel) {
        SessionInfo info = new SessionInfo(sessionId, userId, System.currentTimeMillis());
        sessionMap.put(sessionId, info);
        channelMap.put(sessionId, channel);
        userSessions.computeIfAbsent(userId, k -> new ArrayList<>()).add(sessionId);
    }

    @Override
    public void unbind(String sessionId) {
        SessionInfo info = sessionMap.remove(sessionId);
        channelMap.remove(sessionId);
        if (info != null && info.userId() != null) {
            List<String> sessions = userSessions.get(info.userId());
            if (sessions != null) {
                sessions.remove(sessionId);
                if (sessions.isEmpty()) {
                    userSessions.remove(info.userId());
                }
            }
        }
    }

    @Override
    public List<SessionInfo> getSessions(Long userId) {
        List<String> sessionIds = userSessions.get(userId);
        if (sessionIds == null) return List.of();
        return sessionIds.stream()
                .map(sessionMap::get)
                .filter(s -> s != null)
                .toList();
    }

    @Override
    public boolean isOnline(Long userId) {
        List<String> sessions = userSessions.get(userId);
        return sessions != null && !sessions.isEmpty();
    }

    @Override
    public long getOnlineCount() {
        return userSessions.size();
    }
}
