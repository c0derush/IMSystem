package com.im.client;

import com.im.proto.Packet;

import java.util.concurrent.ScheduledFuture;

/**
 * 待确认消息，用于 ACK 超时重传。
 */
public class PendingAck {

    private final String messageId;
    private final Packet packet;
    private final long sendTime;
    private int retryCount;
    private ScheduledFuture<?> timeoutTask;

    public PendingAck(String messageId, Packet packet) {
        this.messageId = messageId;
        this.packet = packet;
        this.sendTime = System.currentTimeMillis();
        this.retryCount = 0;
    }

    public String getMessageId() {
        return messageId;
    }

    public Packet getPacket() {
        return packet;
    }

    public long getSendTime() {
        return sendTime;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void incrementRetry() {
        this.retryCount++;
    }

    public ScheduledFuture<?> getTimeoutTask() {
        return timeoutTask;
    }

    public void setTimeoutTask(ScheduledFuture<?> timeoutTask) {
        this.timeoutTask = timeoutTask;
    }

    public void cancelTimeout() {
        if (timeoutTask != null && !timeoutTask.isDone()) {
            timeoutTask.cancel(false);
        }
    }
}
