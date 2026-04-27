package com.im.group.api;

import java.util.List;

/**
 * 群消息扩散策略
 * <p>根据群规模决定使用写扩散还是读扩散，返回需要接收此消息的成员 ID 列表
 */
public interface GroupMessageDistributor {

    /**
     * 分发群消息，返回需要接收该消息的成员 ID 列表
     *
     * @param groupId  群 ID
     * @param senderId 发送者用户 ID
     * @return 接收者 ID 列表（已剔除发送者）
     */
    List<Long> distribute(Long groupId, Long senderId);
}
