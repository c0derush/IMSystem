package com.im.group.service;

import com.im.group.api.GroupMemberService;
import com.im.group.api.GroupMessageDistributor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class GroupMessageDistributorImpl implements GroupMessageDistributor {

    private static final int WRITE_DIFFUSION_THRESHOLD = 200;

    private final GroupMemberService groupMemberService;

    public GroupMessageDistributorImpl(GroupMemberService groupMemberService) {
        this.groupMemberService = groupMemberService;
    }

    @Override
    public List<Long> distribute(Long groupId, Long senderId) {
        int memberCount = groupMemberService.getMemberCount(groupId);

        // 大群（>200人）走读扩散，MVP 阶段暂不扩散
        if (memberCount > WRITE_DIFFUSION_THRESHOLD) {
            return List.of();
        }

        // 小群写扩散：返回全部成员，剔除发送者自己
        return groupMemberService.getMemberIds(groupId).stream()
                .filter(id -> !id.equals(senderId))
                .toList();
    }
}
