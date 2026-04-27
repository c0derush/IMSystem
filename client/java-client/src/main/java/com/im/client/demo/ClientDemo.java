package com.im.client.demo;

import com.im.client.*;
import com.im.proto.CreateGroupResp;
import com.im.proto.GroupNotice;
import com.im.proto.GroupOpResp;
import com.im.proto.TextMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Scanner;
import java.util.concurrent.CountDownLatch;

/**
 * Java 客户端命令行演示入口。
 *
 * <p>用法：
 * <pre>
 *   mvn -pl client/java-client -am clean install
 *   mvn -pl client/java-client exec:java
 * </pre>
 */
public class ClientDemo {

    private static final Logger logger = LoggerFactory.getLogger(ClientDemo.class);

    public static void main(String[] args) throws Exception {
        NettyIMClient client = new NettyIMClient();
        CountDownLatch loginLatch = new CountDownLatch(1);

        client.addConnectionListener(new ConnectionListener() {
            @Override
            public void onConnected() {
                logger.info("[Listener] Connected");
            }

            @Override
            public void onDisconnected() {
                logger.info("[Listener] Disconnected");
            }

            @Override
            public void onReconnecting(int attempt) {
                logger.info("[Listener] Reconnecting #{}...", attempt);
            }

            @Override
            public void onLoginSuccess(Long userId, String nickname) {
                logger.info("[Listener] Login success: userId={}, nickname={}", userId, nickname);
                loginLatch.countDown();
            }

            @Override
            public void onLoginFailed(String errorMessage) {
                logger.warn("[Listener] Login failed: {}", errorMessage);
                loginLatch.countDown();
            }
        });

        client.addMessageListener(new MessageListener() {
            @Override
            public void onMessageReceived(TextMessage message) {
                logger.info("[Message] From {}: {}", message.getSenderId(), message.getContent());
            }

            @Override
            public void onMessageAck(String messageId, int status) {
                logger.info("[ACK] messageId={}, status={}", messageId, status);
            }
        });

        client.addGroupOperationListener(new GroupOperationListener() {
            @Override
            public void onCreateGroupResult(CreateGroupResp resp) {
                if (resp.getSuccess()) {
                    logger.info("[Group] Created: id={}, name={}", resp.getGroupId(), resp.getName());
                } else {
                    logger.warn("[Group] Create failed: {}", resp.getErrorMessage());
                }
            }

            @Override
            public void onGroupOpResult(GroupOpResp resp) {
                String opName = switch (resp.getOpType()) {
                    case 1 -> "join";
                    case 2 -> "leave";
                    case 3 -> "kick";
                    case 4 -> "dissolve";
                    default -> "unknown";
                };
                if (resp.getSuccess()) {
                    logger.info("[Group] Op success: {} group {}", opName, resp.getGroupId());
                } else {
                    logger.warn("[Group] Op failed: {} group {}, msg={}", opName, resp.getGroupId(), resp.getErrorMessage());
                }
            }

            @Override
            public void onGroupNoticeReceived(GroupNotice notice) {
                String typeName = switch (notice.getType()) {
                    case 1 -> "member joined";
                    case 2 -> "member left";
                    case 3 -> "member kicked";
                    case 4 -> "group dissolved";
                    default -> "unknown";
                };
                logger.info("[GroupNotice] groupId={}, type={}, operatorId={}, targetUserId={}",
                        notice.getGroupId(), typeName, notice.getOperatorId(), notice.getTargetUserId());
            }
        });

        // 连接服务端（先不传 token，走用户名密码登录）
        client.connect("127.0.0.1", 8081, null);

        Scanner scanner = new Scanner(System.in);
        System.out.println("Commands: register <user> <pass> [nick] | login <user> <pass> | send <toId> <msg> | groupmsg <groupId> <msg> | creategroup <name> | joingroup <groupId> | leavegroup <groupId> | kickmember <groupId> <targetUserId> | dissolvegroup <groupId> | quit");

        while (true) {
            System.out.print("> ");
            String line = scanner.nextLine().trim();
            if (line.isEmpty()) continue;

            String[] parts = line.split("\\s+", 3);
            String cmd = parts[0].toLowerCase();

            try {
                switch (cmd) {
                    case "register" -> {
                        if (parts.length < 3) {
                            System.out.println("Usage: register <username> <password> [nickname]");
                            continue;
                        }
                        String username = parts[1];
                        String password = parts[2];
                        String nickname = username;
                        client.register(username, password, nickname);
                        System.out.println("Register request sent.");
                    }
                    case "login" -> {
                        if (parts.length < 3) {
                            System.out.println("Usage: login <username> <password>");
                            continue;
                        }
                        client.login(parts[1], parts[2]);
                        System.out.println("Login request sent.");
                    }
                    case "send" -> {
                        if (client.getState() != ConnectionState.LOGGED_IN) {
                            System.out.println("Please login first.");
                            continue;
                        }
                        if (parts.length < 3) {
                            System.out.println("Usage: send <receiverId> <message>");
                            continue;
                        }
                        long receiverId = Long.parseLong(parts[1]);
                        String content = parts[2];
                        client.sendSingleMessage(receiverId, content);
                        System.out.println("Message sent.");
                    }
                    case "groupmsg" -> {
                        if (client.getState() != ConnectionState.LOGGED_IN) {
                            System.out.println("Please login first.");
                            continue;
                        }
                        if (parts.length < 3) {
                            System.out.println("Usage: groupmsg <groupId> <message>");
                            continue;
                        }
                        long groupId = Long.parseLong(parts[1]);
                        String content = parts[2];
                        client.sendGroupMessage(groupId, content);
                        System.out.println("Group message sent.");
                    }
                    case "creategroup" -> {
                        if (client.getState() != ConnectionState.LOGGED_IN) {
                            System.out.println("Please login first.");
                            continue;
                        }
                        if (parts.length < 2) {
                            System.out.println("Usage: creategroup <name>");
                            continue;
                        }
                        client.createGroup(parts[1]);
                        System.out.println("Create group request sent.");
                    }
                    case "joingroup" -> {
                        if (client.getState() != ConnectionState.LOGGED_IN) {
                            System.out.println("Please login first.");
                            continue;
                        }
                        if (parts.length < 2) {
                            System.out.println("Usage: joingroup <groupId>");
                            continue;
                        }
                        client.joinGroup(Long.parseLong(parts[1]));
                        System.out.println("Join group request sent.");
                    }
                    case "leavegroup" -> {
                        if (client.getState() != ConnectionState.LOGGED_IN) {
                            System.out.println("Please login first.");
                            continue;
                        }
                        if (parts.length < 2) {
                            System.out.println("Usage: leavegroup <groupId>");
                            continue;
                        }
                        client.leaveGroup(Long.parseLong(parts[1]));
                        System.out.println("Leave group request sent.");
                    }
                    case "kickmember" -> {
                        if (client.getState() != ConnectionState.LOGGED_IN) {
                            System.out.println("Please login first.");
                            continue;
                        }
                        if (parts.length < 3) {
                            System.out.println("Usage: kickmember <groupId> <targetUserId>");
                            continue;
                        }
                        client.kickMember(Long.parseLong(parts[1]), Long.parseLong(parts[2]));
                        System.out.println("Kick member request sent.");
                    }
                    case "dissolvegroup" -> {
                        if (client.getState() != ConnectionState.LOGGED_IN) {
                            System.out.println("Please login first.");
                            continue;
                        }
                        if (parts.length < 2) {
                            System.out.println("Usage: dissolvegroup <groupId>");
                            continue;
                        }
                        client.dissolveGroup(Long.parseLong(parts[1]));
                        System.out.println("Dissolve group request sent.");
                    }
                    case "quit", "exit" -> {
                        client.disconnect();
                        System.out.println("Bye.");
                        return;
                    }
                    default -> System.out.println("Unknown command: " + cmd);
                }
            } catch (Exception e) {
                System.out.println("Error: " + e.getMessage());
            }
        }
    }
}
