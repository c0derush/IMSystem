package com.im.user.api;

import java.util.List;

public interface FriendService {

    List<Long> getFriendIds(Long userId);

    boolean isFriend(Long userId, Long friendId);

    void addFriend(Long userId, Long friendId);

    void removeFriend(Long userId, Long friendId);
}
