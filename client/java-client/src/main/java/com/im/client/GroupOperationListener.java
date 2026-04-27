package com.im.client;

import com.im.proto.CreateGroupResp;
import com.im.proto.GroupNotice;
import com.im.proto.GroupOpResp;

/**
 * 群操作事件监听器。
 */
public interface GroupOperationListener {

    /**
     * 创建群结果回调。
     */
    void onCreateGroupResult(CreateGroupResp resp);

    /**
     * 群操作结果回调（join/leave/kick/dissolve）。
     *
     * @param resp GroupOpResp
     */
    void onGroupOpResult(GroupOpResp resp);

    /**
     * 收到群成员变更通知。
     *
     * @param notice GroupNotice
     */
    void onGroupNoticeReceived(GroupNotice notice);
}
