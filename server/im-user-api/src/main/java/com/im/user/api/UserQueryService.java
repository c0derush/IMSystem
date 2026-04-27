package com.im.user.api;

import com.im.user.api.dto.UserInfo;

import java.util.List;

public interface UserQueryService {

    UserInfo getUserById(Long userId);

    List<UserInfo> getUsersByIds(List<Long> userIds);

    boolean exists(Long userId);
}
