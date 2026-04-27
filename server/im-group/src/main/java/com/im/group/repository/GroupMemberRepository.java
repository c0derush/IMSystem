package com.im.group.repository;

import com.im.group.entity.GroupMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GroupMemberRepository extends JpaRepository<GroupMember, Long> {

    List<GroupMember> findByGroupId(Long groupId);

    boolean existsByGroupIdAndUserId(Long groupId, Long userId);

    int countByGroupId(Long groupId);

    void deleteByGroupIdAndUserId(Long groupId, Long userId);
}
