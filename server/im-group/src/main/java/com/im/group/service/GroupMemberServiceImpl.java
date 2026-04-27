package com.im.group.service;

import com.im.group.api.GroupMemberService;
import com.im.group.entity.Group;
import com.im.group.entity.GroupMember;
import com.im.group.repository.GroupMemberRepository;
import com.im.group.repository.GroupRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class GroupMemberServiceImpl implements GroupMemberService {

    private final GroupMemberRepository groupMemberRepository;
    private final GroupRepository groupRepository;

    public GroupMemberServiceImpl(GroupMemberRepository groupMemberRepository,
                                  GroupRepository groupRepository) {
        this.groupMemberRepository = groupMemberRepository;
        this.groupRepository = groupRepository;
    }

    @Override
    @Transactional
    public void joinGroup(Long groupId, Long userId) {
        if (groupMemberRepository.existsByGroupIdAndUserId(groupId, userId)) {
            return;
        }
        GroupMember member = new GroupMember();
        member.setGroupId(groupId);
        member.setUserId(userId);
        groupMemberRepository.save(member);

        // 更新成员计数
        Group group = groupRepository.findById(groupId).orElse(null);
        if (group != null) {
            group.setMemberCount(groupMemberRepository.countByGroupId(groupId));
            groupRepository.save(group);
        }
    }

    @Override
    @Transactional
    public void leaveGroup(Long groupId, Long userId) {
        groupMemberRepository.deleteByGroupIdAndUserId(groupId, userId);

        Group group = groupRepository.findById(groupId).orElse(null);
        if (group != null) {
            group.setMemberCount(groupMemberRepository.countByGroupId(groupId));
            groupRepository.save(group);
        }
    }

    @Override
    @Transactional
    public void kickMember(Long groupId, Long operatorId, Long targetUserId) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Group not found: " + groupId));
        if (!group.getCreatorId().equals(operatorId)) {
            throw new IllegalArgumentException("Only creator can kick members");
        }
        groupMemberRepository.deleteByGroupIdAndUserId(groupId, targetUserId);

        group.setMemberCount(groupMemberRepository.countByGroupId(groupId));
        groupRepository.save(group);
    }

    @Override
    public List<Long> getMemberIds(Long groupId) {
        return groupMemberRepository.findByGroupId(groupId).stream()
                .map(GroupMember::getUserId)
                .toList();
    }

    @Override
    public boolean isMember(Long groupId, Long userId) {
        return groupMemberRepository.existsByGroupIdAndUserId(groupId, userId);
    }

    @Override
    public int getMemberCount(Long groupId) {
        return groupMemberRepository.countByGroupId(groupId);
    }
}
