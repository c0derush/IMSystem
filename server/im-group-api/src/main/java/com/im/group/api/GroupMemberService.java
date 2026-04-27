package com.im.group.api;

import java.util.List;

/**
 * 群成员服务
 */
public interface GroupMemberService {

    /**
     * 加入群
     *
     * @param groupId 群 ID
     * @param userId  用户 ID
     */
    void joinGroup(Long groupId, Long userId);

    /**
     * 退出群
     *
     * @param groupId 群 ID
     * @param userId  用户 ID
     */
    void leaveGroup(Long groupId, Long userId);

    /**
     * 踢出成员
     *
     * @param groupId      群 ID
     * @param operatorId   操作者用户 ID
     * @param targetUserId 被踢用户 ID
     */
    void kickMember(Long groupId, Long operatorId, Long targetUserId);

    /**
     * 获取群成员 ID 列表
     *
     * @param groupId 群 ID
     * @return 成员 ID 列表
     */
    List<Long> getMemberIds(Long groupId);

    /**
     * 判断用户是否在群中
     *
     * @param groupId 群 ID
     * @param userId  用户 ID
     * @return true 是成员
     */
    boolean isMember(Long groupId, Long userId);

    /**
     * 获取群成员数量
     *
     * @param groupId 群 ID
     * @return 成员数量
     */
    int getMemberCount(Long groupId);
}
