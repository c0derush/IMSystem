package com.im.client;

/**
 * IM 客户端门面接口。
 *
 * <p>提供连接、消息收发与事件监听能力。
 */
public interface IMClient {

    /**
     * 建立 TCP 连接并登录。
     *
     * @param host  服务端地址
     * @param port  服务端端口
     * @param token 登录凭证（若已实现注册/登录分离，可先传空字符串再调用登录接口）
     */
    void connect(String host, int port, String token);

    /**
     * 断开连接并释放资源。
     */
    void disconnect();

    /**
     * 发送单聊消息。
     *
     * @param receiverId 接收者用户 ID
     * @param content    文本内容
     */
    void sendSingleMessage(Long receiverId, String content);

    /**
     * 发送群消息。
     *
     * @param groupId 群组 ID
     * @param content 文本内容
     */
    void sendGroupMessage(Long groupId, String content);

    /**
     * 注册消息监听器。
     */
    void addMessageListener(MessageListener listener);

    /**
     * 移除消息监听器。
     */
    void removeMessageListener(MessageListener listener);

    /**
     * 注册连接状态监听器。
     */
    void addConnectionListener(ConnectionListener listener);

    /**
     * 移除连接状态监听器。
     */
    void removeConnectionListener(ConnectionListener listener);

    /**
     * 当前连接状态。
     */
    ConnectionState getState();

    /**
     * 阻塞直到连接到达指定状态（或超时）。
     */
    boolean awaitState(ConnectionState state, long timeoutMillis);

    /**
     * 创建群。
     *
     * @param name 群名称
     */
    void createGroup(String name);

    /**
     * 加入群。
     *
     * @param groupId 群 ID
     */
    void joinGroup(Long groupId);

    /**
     * 退出群。
     *
     * @param groupId 群 ID
     */
    void leaveGroup(Long groupId);

    /**
     * 踢出群成员（仅群主可用）。
     *
     * @param groupId      群 ID
     * @param targetUserId 被踢用户 ID
     */
    void kickMember(Long groupId, Long targetUserId);

    /**
     * 解散群（仅群主可用）。
     *
     * @param groupId 群 ID
     */
    void dissolveGroup(Long groupId);

    /**
     * 注册群操作监听器。
     */
    void addGroupOperationListener(GroupOperationListener listener);

    /**
     * 移除群操作监听器。
     */
    void removeGroupOperationListener(GroupOperationListener listener);
}
