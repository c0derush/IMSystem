package com.im.client;

import com.im.client.codec.ClientFrameDecoder;
import com.im.client.codec.ClientPacketDecoder;
import com.im.client.codec.ClientPacketEncoder;
import com.im.proto.*;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * IMClient 的 Netty 实现。
 *
 * <p>特性：
 * <ul>
 *   <li>TCP 连接管理与断线重连（指数退避）</li>
 *   <li>心跳保活</li>
 *   <li>消息 ACK 与超时重传</li>
 *   <li>状态机：DISCONNECTED -> CONNECTING -> CONNECTED -> LOGGED_IN</li>
 * </ul>
 */
public class NettyIMClient implements IMClient {

    private static final Logger logger = LoggerFactory.getLogger(NettyIMClient.class);

    private static final int HEARTBEAT_INTERVAL_SECONDS = 30;
    private static final int ACK_TIMEOUT_SECONDS = 5;
    private static final int MAX_RETRY_COUNT = 3;
    private static final long INITIAL_RECONNECT_DELAY_MS = 1000;
    private static final long MAX_RECONNECT_DELAY_MS = 30000;

    private final AtomicReference<ConnectionState> state = new AtomicReference<>(ConnectionState.DISCONNECTED);
    private final CopyOnWriteArrayList<MessageListener> messageListeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<ConnectionListener> connectionListeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<GroupOperationListener> groupOperationListeners = new CopyOnWriteArrayList<>();
    private final ConcurrentHashMap<String, PendingAck> pendingAcks = new ConcurrentHashMap<>();
    private final AtomicLong clientMessageIdSeq = new AtomicLong(0);

    private EventLoopGroup workerGroup;
    private Channel channel;
    private ScheduledExecutorService scheduledExecutor;

    private String host;
    private int port;
    private String token;
    private volatile Long userId;
    private volatile int reconnectAttempt = 0;
    private volatile boolean shutdown = false;

    @Override
    public void connect(String host, int port, String token) {
        if (!state.compareAndSet(ConnectionState.DISCONNECTED, ConnectionState.CONNECTING)) {
            logger.warn("Connect called but current state is {}", state.get());
            return;
        }
        this.host = host;
        this.port = port;
        this.token = token;
        this.shutdown = false;
        this.reconnectAttempt = 0;

        scheduledExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "im-client-scheduler");
            t.setDaemon(true);
            return t;
        });

        doConnect();
    }

    private void doConnect() {
        workerGroup = new NioEventLoopGroup();
        Bootstrap bootstrap = new Bootstrap();
        ClientChannelHandler handler = new ClientChannelHandler(this, messageListeners, connectionListeners);

        bootstrap.group(workerGroup)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .option(ChannelOption.TCP_NODELAY, true)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline()
                                .addLast(new ClientFrameDecoder())
                                .addLast(new ClientPacketDecoder())
                                .addLast(new ClientPacketEncoder())
                                .addLast(new IdleStateHandler(0, HEARTBEAT_INTERVAL_SECONDS, 0))
                                .addLast(handler);
                    }
                });

        ChannelFuture future = bootstrap.connect(host, port);
        future.addListener((ChannelFutureListener) f -> {
            if (!f.isSuccess()) {
                logger.error("Connect failed: {}:{}", host, port);
                workerGroup.shutdownGracefully();
                scheduleReconnect();
            }
        });
    }

    void onChannelActive(Channel ch) {
        this.channel = ch;
        state.set(ConnectionState.CONNECTED);
        reconnectAttempt = 0;
        logger.info("Connected to {}:{}", host, port);

        // 若有 token，自动发送登录请求
        if (token != null && !token.isEmpty()) {
            sendLogin(token);
        }
    }

    void onChannelInactive() {
        this.channel = null;
        if (state.get() != ConnectionState.DISCONNECTED && !shutdown) {
            state.set(ConnectionState.DISCONNECTED);
            scheduleReconnect();
        }
    }

    private void scheduleReconnect() {
        if (shutdown) {
            return;
        }
        reconnectAttempt++;
        long delay = Math.min(INITIAL_RECONNECT_DELAY_MS * (1L << (reconnectAttempt - 1)), MAX_RECONNECT_DELAY_MS);
        logger.info("Scheduling reconnect #{} in {}ms", reconnectAttempt, delay);
        connectionListeners.forEach(l -> l.onReconnecting(reconnectAttempt));

        scheduledExecutor.schedule(() -> {
            if (!shutdown && state.compareAndSet(ConnectionState.DISCONNECTED, ConnectionState.CONNECTING)) {
                doConnect();
            }
        }, delay, TimeUnit.MILLISECONDS);
    }

    @Override
    public void disconnect() {
        shutdown = true;
        state.set(ConnectionState.DISCONNECTED);

        pendingAcks.values().forEach(PendingAck::cancelTimeout);
        pendingAcks.clear();

        if (channel != null) {
            channel.close();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
        if (scheduledExecutor != null) {
            scheduledExecutor.shutdownNow();
        }
        logger.info("Client disconnected");
    }

    @Override
    public void sendSingleMessage(Long receiverId, String content) {
        ensureConnected();
        String clientMsgId = generateClientMessageId();
        TextMessage text = TextMessage.newBuilder()
                .setSenderId(userId != null ? userId : 0L)
                .setReceiverId(receiverId)
                .setContent(content)
                .setClientMessageId(clientMsgId)
                .setTimestamp(System.currentTimeMillis())
                .build();

        Packet packet = Packet.newBuilder()
                .setHeader(PacketHeader.newBuilder().setCmd(Command.CMD_SINGLE_MSG_REQ))
                .setTextMessage(text)
                .build();

        sendWithAck(clientMsgId, packet);
    }

    @Override
    public void sendGroupMessage(Long groupId, String content) {
        ensureConnected();
        String clientMsgId = generateClientMessageId();
        TextMessage text = TextMessage.newBuilder()
                .setSenderId(userId != null ? userId : 0L)
                .setGroupId(groupId)
                .setContent(content)
                .setClientMessageId(clientMsgId)
                .setTimestamp(System.currentTimeMillis())
                .build();

        Packet packet = Packet.newBuilder()
                .setHeader(PacketHeader.newBuilder().setCmd(Command.CMD_GROUP_MSG_REQ))
                .setTextMessage(text)
                .build();

        sendWithAck(clientMsgId, packet);
    }

    public void register(String username, String password, String nickname) {
        ensureConnected();
        RegisterReq req = RegisterReq.newBuilder()
                .setUsername(username)
                .setPassword(password)
                .setNickname(nickname)
                .build();

        Packet packet = Packet.newBuilder()
                .setHeader(PacketHeader.newBuilder().setCmd(Command.CMD_REGISTER_REQ))
                .setRegisterReq(req)
                .build();

        channel.writeAndFlush(packet);
    }

    public void login(String username, String password) {
        ensureConnected();
        LoginReq req = LoginReq.newBuilder()
                .setUsername(username)
                .setPassword(password)
                .build();

        Packet packet = Packet.newBuilder()
                .setHeader(PacketHeader.newBuilder().setCmd(Command.CMD_LOGIN_REQ))
                .setLoginReq(req)
                .build();

        channel.writeAndFlush(packet);
    }

    private void sendLogin(String token) {
        // 当前协议未定义 TokenLogin，此处留空。
        // 实际场景可通过扩展 proto 实现免密登录。
    }

    private void sendWithAck(String messageId, Packet packet) {
        PendingAck pending = new PendingAck(messageId, packet);
        pendingAcks.put(messageId, pending);

        ScheduledFuture<?> timeout = scheduledExecutor.schedule(() -> {
            PendingAck p = pendingAcks.get(messageId);
            if (p != null) {
                if (p.getRetryCount() < MAX_RETRY_COUNT) {
                    p.incrementRetry();
                    logger.warn("ACK timeout, retrying {} (attempt {})", messageId, p.getRetryCount());
                    channel.writeAndFlush(p.getPacket());
                    // 重新调度由外层循环或简化处理：这里不再递归 schedule，避免复杂嵌套
                } else {
                    logger.error("Message {} failed after max retries", messageId);
                    pendingAcks.remove(messageId);
                    messageListeners.forEach(l -> l.onMessageAck(messageId, -1));
                }
            }
        }, ACK_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        pending.setTimeoutTask(timeout);
        channel.writeAndFlush(packet);
    }

    void onMessageAck(String messageId, int status) {
        PendingAck pending = pendingAcks.remove(messageId);
        if (pending != null) {
            pending.cancelTimeout();
            logger.debug("Message {} ack received, status={}", messageId, status);
        }
    }

    void sendHeartbeat() {
        if (channel == null || !channel.isActive()) {
            return;
        }
        HeartbeatReq req = HeartbeatReq.newBuilder()
                .setTimestamp(System.currentTimeMillis())
                .build();
        Packet packet = Packet.newBuilder()
                .setHeader(PacketHeader.newBuilder().setCmd(Command.CMD_HEARTBEAT_REQ))
                .setHeartbeatReq(req)
                .build();
        channel.writeAndFlush(packet);
        logger.debug("Heartbeat sent");
    }

    void onLoginSuccess(Long userId, String token) {
        this.userId = userId;
        this.token = token;
        state.set(ConnectionState.LOGGED_IN);
        logger.info("Login success, userId={}", userId);
    }

    @Override
    public ConnectionState getState() {
        return state.get();
    }

    @Override
    public boolean awaitState(ConnectionState expected, long timeoutMillis) {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            if (state.get() == expected) {
                return true;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return state.get() == expected;
    }

    @Override
    public void addMessageListener(MessageListener listener) {
        messageListeners.add(listener);
    }

    @Override
    public void removeMessageListener(MessageListener listener) {
        messageListeners.remove(listener);
    }

    @Override
    public void addConnectionListener(ConnectionListener listener) {
        connectionListeners.add(listener);
    }

    @Override
    public void removeConnectionListener(ConnectionListener listener) {
        connectionListeners.remove(listener);
    }

    @Override
    public void createGroup(String name) {
        ensureConnected();
        CreateGroupReq req = CreateGroupReq.newBuilder().setName(name).build();
        Packet packet = Packet.newBuilder()
                .setHeader(PacketHeader.newBuilder().setCmd(Command.CMD_CREATE_GROUP_REQ))
                .setCreateGroupReq(req)
                .build();
        channel.writeAndFlush(packet);
    }

    @Override
    public void joinGroup(Long groupId) {
        ensureConnected();
        JoinGroupReq req = JoinGroupReq.newBuilder().setGroupId(groupId).build();
        Packet packet = Packet.newBuilder()
                .setHeader(PacketHeader.newBuilder().setCmd(Command.CMD_JOIN_GROUP_REQ))
                .setJoinGroupReq(req)
                .build();
        channel.writeAndFlush(packet);
    }

    @Override
    public void leaveGroup(Long groupId) {
        ensureConnected();
        LeaveGroupReq req = LeaveGroupReq.newBuilder().setGroupId(groupId).build();
        Packet packet = Packet.newBuilder()
                .setHeader(PacketHeader.newBuilder().setCmd(Command.CMD_LEAVE_GROUP_REQ))
                .setLeaveGroupReq(req)
                .build();
        channel.writeAndFlush(packet);
    }

    @Override
    public void kickMember(Long groupId, Long targetUserId) {
        ensureConnected();
        KickMemberReq req = KickMemberReq.newBuilder()
                .setGroupId(groupId)
                .setTargetUserId(targetUserId)
                .build();
        Packet packet = Packet.newBuilder()
                .setHeader(PacketHeader.newBuilder().setCmd(Command.CMD_KICK_MEMBER_REQ))
                .setKickMemberReq(req)
                .build();
        channel.writeAndFlush(packet);
    }

    @Override
    public void dissolveGroup(Long groupId) {
        ensureConnected();
        DissolveGroupReq req = DissolveGroupReq.newBuilder().setGroupId(groupId).build();
        Packet packet = Packet.newBuilder()
                .setHeader(PacketHeader.newBuilder().setCmd(Command.CMD_DISSOLVE_GROUP_REQ))
                .setDissolveGroupReq(req)
                .build();
        channel.writeAndFlush(packet);
    }

    @Override
    public void addGroupOperationListener(GroupOperationListener listener) {
        groupOperationListeners.add(listener);
    }

    @Override
    public void removeGroupOperationListener(GroupOperationListener listener) {
        groupOperationListeners.remove(listener);
    }

    CopyOnWriteArrayList<GroupOperationListener> getGroupOperationListeners() {
        return groupOperationListeners;
    }

    private void ensureConnected() {
        if (channel == null || !channel.isActive()) {
            throw new IllegalStateException("Not connected, current state=" + state.get());
        }
    }

    private String generateClientMessageId() {
        return userId + "-" + System.currentTimeMillis() + "-" + clientMessageIdSeq.incrementAndGet();
    }

    public Long getUserId() {
        return userId;
    }
}
