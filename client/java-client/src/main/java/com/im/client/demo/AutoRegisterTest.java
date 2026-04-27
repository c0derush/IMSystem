package com.im.client.demo;

import com.im.client.*;
import com.im.proto.TextMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class AutoRegisterTest {

    private static final Logger logger = LoggerFactory.getLogger(AutoRegisterTest.class);

    public static void main(String[] args) throws Exception {
        NettyIMClient client = new NettyIMClient();
        CountDownLatch latch = new CountDownLatch(1);

        client.addConnectionListener(new ConnectionListener() {
            @Override public void onConnected() { logger.info("[Listener] Connected"); }
            @Override public void onDisconnected() { logger.info("[Listener] Disconnected"); }
            @Override public void onReconnecting(int attempt) { logger.info("[Listener] Reconnecting #{}...", attempt); }
            @Override public void onLoginSuccess(Long userId, String nickname) {
                logger.info("[Listener] Login/Register success: userId={}, nickname={}", userId, nickname);
                latch.countDown();
            }
            @Override public void onLoginFailed(String errorMessage) {
                logger.warn("[Listener] Login/Register failed: {}", errorMessage);
                latch.countDown();
            }
        });

        client.addMessageListener(new MessageListener() {
            @Override public void onMessageReceived(TextMessage message) {}
            @Override public void onMessageAck(String messageId, int status) {}
        });

        client.connect("127.0.0.1", 8081, null);

        // 等待连接建立
        Thread.sleep(1000);

        logger.info("Sending register request...");
        client.register("testuser" + System.currentTimeMillis(), "testpass", "TestNick");

        boolean received = latch.await(10, TimeUnit.SECONDS);
        if (!received) {
            logger.error("No response received within 10 seconds");
        }

        client.disconnect();
    }
}
