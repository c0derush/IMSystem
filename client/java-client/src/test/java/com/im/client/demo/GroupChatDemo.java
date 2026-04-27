package com.im.client.demo;

import com.im.client.*;
import com.im.proto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 群聊端到端测试 Demo。
 *
 * <p>场景：
 * <ol>
 *   <li>用户 A/B/C 分别注册并登录</li>
 *   <li>A 创建群，获取 groupId</li>
 *   <li>B 加入群，A/B 收到成员加入通知</li>
 *   <li>A 发送群消息，B 收到消息</li>
 *   <li>A 踢出 B，A/B 收到踢人通知</li>
 *   <li>C 加入群，A 解散群，A/C 收到解散通知</li>
 * </ol>
 */
public class GroupChatDemo {

    private static final Logger logger = LoggerFactory.getLogger(GroupChatDemo.class);
    private static final String HOST = "127.0.0.1";
    private static final int PORT = 8081;

    public static void main(String[] args) throws Exception {
        // 1. 创建三个客户端
        NettyIMClient clientA = new NettyIMClient();
        NettyIMClient clientB = new NettyIMClient();
        NettyIMClient clientC = new NettyIMClient();

        try {
            // 2. 注册并登录
            Long userA = registerAndLogin(clientA, "demo_user_a", "pass_a", "UserA");
            Long userB = registerAndLogin(clientB, "demo_user_b", "pass_b", "UserB");
            Long userC = registerAndLogin(clientC, "demo_user_c", "pass_c", "UserC");

            logger.info("All users logged in: A={}, B={}, C={}", userA, userB, userC);

            // 3. A 创建群
            AtomicReference<Long> groupIdRef = new AtomicReference<>();
            CountDownLatch createLatch = new CountDownLatch(1);
            clientA.addGroupOperationListener(new GroupOperationListener() {
                @Override
                public void onCreateGroupResult(CreateGroupResp resp) {
                    if (resp.getSuccess()) {
                        groupIdRef.set(resp.getGroupId());
                        logger.info("[A] Created group: id={}, name={}", resp.getGroupId(), resp.getName());
                    } else {
                        logger.error("[A] Create group failed: {}", resp.getErrorMessage());
                    }
                    createLatch.countDown();
                }

                @Override
                public void onGroupOpResult(GroupOpResp resp) {
                    logger.info("[A] GroupOpResult: type={}, success={}, groupId={}",
                            resp.getOpType(), resp.getSuccess(), resp.getGroupId());
                }

                @Override
                public void onGroupNoticeReceived(GroupNotice notice) {
                    logger.info("[A] GroupNotice: groupId={}, type={}, operatorId={}, targetUserId={}",
                            notice.getGroupId(), notice.getType(), notice.getOperatorId(), notice.getTargetUserId());
                }
            });

            clientA.createGroup("DemoGroup");
            boolean created = createLatch.await(10, TimeUnit.SECONDS);
            if (!created || groupIdRef.get() == null) {
                throw new RuntimeException("Create group timeout or failed");
            }
            Long groupId = groupIdRef.get();

            // 4. B 加入群
            CountDownLatch bJoinLatch = new CountDownLatch(2); // A 和 B 都收到通知
            clientB.addGroupOperationListener(new GroupOperationListener() {
                @Override
                public void onCreateGroupResult(CreateGroupResp resp) {
                }

                @Override
                public void onGroupOpResult(GroupOpResp resp) {
                    if (resp.getOpType() == 1 && resp.getSuccess()) {
                        logger.info("[B] Joined group {} successfully", resp.getGroupId());
                        bJoinLatch.countDown();
                    }
                }

                @Override
                public void onGroupNoticeReceived(GroupNotice notice) {
                    if (notice.getType() == 1) {
                        logger.info("[B] Received join notice for group {}", notice.getGroupId());
                        bJoinLatch.countDown();
                    }
                }
            });

            // A 监听 B 的加入通知
            clientA.addGroupOperationListener(new GroupOperationListener() {
                @Override
                public void onCreateGroupResult(CreateGroupResp resp) {
                }

                @Override
                public void onGroupOpResult(GroupOpResp resp) {
                }

                @Override
                public void onGroupNoticeReceived(GroupNotice notice) {
                    if (notice.getType() == 1 && notice.getGroupId() == groupId) {
                        logger.info("[A] Received join notice: user {} joined", notice.getTargetUserId());
                        bJoinLatch.countDown();
                    }
                }
            });

            clientB.joinGroup(groupId);
            boolean joined = bJoinLatch.await(10, TimeUnit.SECONDS);
            if (!joined) {
                logger.warn("Join group notification timeout");
            }

            // 5. A 发送群消息，B 接收
            CountDownLatch msgLatch = new CountDownLatch(1);
            clientB.addMessageListener(new MessageListener() {
                @Override
                public void onMessageReceived(TextMessage message) {
                    if (message.getGroupId() == groupId) {
                        logger.info("[B] Received group message from {}: {}", message.getSenderId(), message.getContent());
                        msgLatch.countDown();
                    }
                }

                @Override
                public void onMessageAck(String messageId, int status) {
                }
            });

            clientA.sendGroupMessage(groupId, "Hello Group!");
            boolean msgReceived = msgLatch.await(10, TimeUnit.SECONDS);
            if (!msgReceived) {
                logger.warn("Group message receive timeout");
            }

            // 6. A 踢出 B
            CountDownLatch kickLatch = new CountDownLatch(2); // A 和 B 都收到踢人通知
            clientB.addGroupOperationListener(new GroupOperationListener() {
                @Override
                public void onCreateGroupResult(CreateGroupResp resp) {
                }

                @Override
                public void onGroupOpResult(GroupOpResp resp) {
                    if (resp.getOpType() == 3 && resp.getSuccess()) {
                        kickLatch.countDown();
                    }
                }

                @Override
                public void onGroupNoticeReceived(GroupNotice notice) {
                    if (notice.getType() == 3 && notice.getGroupId() == groupId) {
                        logger.info("[B] Received kick notice: kicked by {}", notice.getOperatorId());
                        kickLatch.countDown();
                    }
                }
            });

            clientA.addGroupOperationListener(new GroupOperationListener() {
                @Override
                public void onCreateGroupResult(CreateGroupResp resp) {
                }

                @Override
                public void onGroupOpResult(GroupOpResp resp) {
                    if (resp.getOpType() == 3 && resp.getSuccess()) {
                        kickLatch.countDown();
                    }
                }

                @Override
                public void onGroupNoticeReceived(GroupNotice notice) {
                    if (notice.getType() == 3 && notice.getGroupId() == groupId) {
                        logger.info("[A] Received kick notice: kicked user {}", notice.getTargetUserId());
                        kickLatch.countDown();
                    }
                }
            });

            clientA.kickMember(groupId, userB);
            boolean kicked = kickLatch.await(10, TimeUnit.SECONDS);
            if (!kicked) {
                logger.warn("Kick member notification timeout");
            }

            // 7. C 加入群，然后 A 解散群
            CountDownLatch cJoinLatch = new CountDownLatch(1);
            clientC.addGroupOperationListener(new GroupOperationListener() {
                @Override
                public void onCreateGroupResult(CreateGroupResp resp) {
                }

                @Override
                public void onGroupOpResult(GroupOpResp resp) {
                    if (resp.getOpType() == 1 && resp.getSuccess()) {
                        cJoinLatch.countDown();
                    }
                }

                @Override
                public void onGroupNoticeReceived(GroupNotice notice) {
                }
            });

            clientC.joinGroup(groupId);
            cJoinLatch.await(10, TimeUnit.SECONDS);

            CountDownLatch dissolveLatch = new CountDownLatch(2); // A 和 C 都收到解散通知
            clientC.addGroupOperationListener(new GroupOperationListener() {
                @Override
                public void onCreateGroupResult(CreateGroupResp resp) {
                }

                @Override
                public void onGroupOpResult(GroupOpResp resp) {
                    if (resp.getOpType() == 4 && resp.getSuccess()) {
                        dissolveLatch.countDown();
                    }
                }

                @Override
                public void onGroupNoticeReceived(GroupNotice notice) {
                    if (notice.getType() == 4 && notice.getGroupId() == groupId) {
                        logger.info("[C] Received dissolve notice for group {}", notice.getGroupId());
                        dissolveLatch.countDown();
                    }
                }
            });

            clientA.addGroupOperationListener(new GroupOperationListener() {
                @Override
                public void onCreateGroupResult(CreateGroupResp resp) {
                }

                @Override
                public void onGroupOpResult(GroupOpResp resp) {
                    if (resp.getOpType() == 4 && resp.getSuccess()) {
                        dissolveLatch.countDown();
                    }
                }

                @Override
                public void onGroupNoticeReceived(GroupNotice notice) {
                    if (notice.getType() == 4 && notice.getGroupId() == groupId) {
                        logger.info("[A] Received dissolve notice for group {}", notice.getGroupId());
                        dissolveLatch.countDown();
                    }
                }
            });

            clientA.dissolveGroup(groupId);
            boolean dissolved = dissolveLatch.await(10, TimeUnit.SECONDS);
            if (!dissolved) {
                logger.warn("Dissolve group notification timeout");
            }

            logger.info("GroupChatDemo completed successfully");

        } finally {
            clientA.disconnect();
            clientB.disconnect();
            clientC.disconnect();
        }
    }

    private static Long registerAndLogin(NettyIMClient client, String username, String password, String nickname) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Long> userIdRef = new AtomicReference<>();

        client.addConnectionListener(new ConnectionListener() {
            @Override
            public void onConnected() {
            }

            @Override
            public void onDisconnected() {
            }

            @Override
            public void onReconnecting(int attempt) {
            }

            @Override
            public void onLoginSuccess(Long userId, String nickname) {
                userIdRef.set(userId);
                latch.countDown();
            }

            @Override
            public void onLoginFailed(String errorMessage) {
                logger.error("Login failed for {}: {}", username, errorMessage);
                latch.countDown();
            }
        });

        client.connect(HOST, PORT, null);
        Thread.sleep(500); // 等待连接建立

        client.register(username, password, nickname);
        boolean registered = latch.await(10, TimeUnit.SECONDS);
        if (!registered || userIdRef.get() == null) {
            throw new RuntimeException("Register/login failed for " + username);
        }
        return userIdRef.get();
    }
}
