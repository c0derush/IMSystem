package com.im.user.service;

import com.im.user.api.UserQueryService;
import com.im.user.api.dto.UserInfo;
import com.im.user.entity.User;
import com.im.user.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserQueryServiceImpl implements UserQueryService {

    private final UserRepository userRepository;

    public UserQueryServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserInfo getUserById(Long userId) {
        return userRepository.findById(userId)
                .map(this::toInfo)
                .orElse(null);
    }

    @Override
    public List<UserInfo> getUsersByIds(List<Long> userIds) {
        return userRepository.findAllById(userIds).stream()
                .map(this::toInfo)
                .toList();
    }

    @Override
    public boolean exists(Long userId) {
        return userRepository.existsById(userId);
    }

    private UserInfo toInfo(User user) {
        return new UserInfo(user.getId(), user.getUsername(), user.getNickname());
    }
}
