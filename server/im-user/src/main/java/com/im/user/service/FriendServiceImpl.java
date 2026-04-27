package com.im.user.service;

import com.im.user.api.FriendService;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class FriendServiceImpl implements FriendService {

    private final Map<Long, Set<Long>> friendMap = new ConcurrentHashMap<>();

    @Override
    public List<Long> getFriendIds(Long userId) {
        Set<Long> friends = friendMap.get(userId);
        return friends == null ? Collections.emptyList() : List.copyOf(friends);
    }

    @Override
    public boolean isFriend(Long userId, Long friendId) {
        Set<Long> friends = friendMap.get(userId);
        return friends != null && friends.contains(friendId);
    }

    @Override
    public void addFriend(Long userId, Long friendId) {
        friendMap.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(friendId);
        friendMap.computeIfAbsent(friendId, k -> ConcurrentHashMap.newKeySet()).add(userId);
    }

    @Override
    public void removeFriend(Long userId, Long friendId) {
        Set<Long> userFriends = friendMap.get(userId);
        if (userFriends != null) userFriends.remove(friendId);
        Set<Long> otherFriends = friendMap.get(friendId);
        if (otherFriends != null) otherFriends.remove(userId);
    }
}
