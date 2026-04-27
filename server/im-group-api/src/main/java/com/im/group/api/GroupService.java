package com.im.group.api;

/**
 * 群核心服务
 */
public interface GroupService {

    /**
     * 创建群
     *
     * @param creatorId 创建者用户 ID
     * @param name      群名称
     * @return 群信息
     */
    GroupInfo createGroup(Long creatorId, String name);

    /**
     * 解散群
     *
     * @param groupId   群 ID
     * @param operatorId 操作者用户 ID（需为群主）
     */
    void dissolveGroup(Long groupId, Long operatorId);

    /**
     * 获取群信息
     *
     * @param groupId 群 ID
     * @return 群信息
     */
    GroupInfo getGroupInfo(Long groupId);

    /**
     * 判断群是否存在
     *
     * @param groupId 群 ID
     * @return true 存在
     */
    boolean exists(Long groupId);
}
