package com.im.client;

/**
 * 连接状态监听回调。
 */
public interface ConnectionListener {

    /**
     * TCP 连接建立成功。
     */
    void onConnected();

    /**
     * TCP 连接断开。
     */
    void onDisconnected();

    /**
     * 正在重连。
     *
     * @param attempt 第几次重连（从 1 开始）
     */
    void onReconnecting(int attempt);

    /**
     * 登录成功（收到 LoginResp 且 success=true）。
     */
    void onLoginSuccess(Long userId, String nickname);

    /**
     * 登录失败。
     */
    void onLoginFailed(String errorMessage);
}
