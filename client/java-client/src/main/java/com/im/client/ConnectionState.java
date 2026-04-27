package com.im.client;

/**
 * 客户端连接状态机。
 */
public enum ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    LOGGED_IN
}
