package com.im.group.service;

import com.im.common.util.SnowflakeId;
import com.im.group.api.GroupInfo;
import com.im.group.api.GroupService;
import com.im.group.entity.Group;
import com.im.group.repository.GroupRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GroupServiceImpl implements GroupService {

    private final GroupRepository groupRepository;
    private final GroupMemberServiceImpl groupMemberService;
    private final SnowflakeId snowflakeId = new SnowflakeId(2, 1);

    public GroupServiceImpl(GroupRepository groupRepository,
                            GroupMemberServiceImpl groupMemberService) {
        this.groupRepository = groupRepository;
        this.groupMemberService = groupMemberService;
    }

    @Override
    @Transactional
    public GroupInfo createGroup(Long creatorId, String name) {
        Group group = new Group();
        group.setId(snowflakeId.nextId());
        group.setName(name);
        group.setCreatorId(creatorId);
        group.setMemberCount(1);
        groupRepository.save(group);

        // 创建者自动加入群
        groupMemberService.joinGroup(group.getId(), creatorId);

        return toDto(group);
    }

    @Override
    @Transactional
    public void dissolveGroup(Long groupId, Long operatorId) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Group not found: " + groupId));
        if (!group.getCreatorId().equals(operatorId)) {
            throw new IllegalArgumentException("Only creator can dissolve group");
        }
        groupRepository.delete(group);
    }

    @Override
    public GroupInfo getGroupInfo(Long groupId) {
        return groupRepository.findById(groupId)
                .map(this::toDto)
                .orElse(null);
    }

    @Override
    public boolean exists(Long groupId) {
        return groupId != null && groupRepository.existsById(groupId);
    }

    private GroupInfo toDto(Group group) {
        return new GroupInfo(
                group.getId(),
                group.getName(),
                group.getCreatorId(),
                group.getMemberCount(),
                group.getCreatedAt() != null ? group.getCreatedAt().toEpochSecond(java.time.ZoneOffset.UTC) * 1000 : null
        );
    }
}
