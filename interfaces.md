# IM 系统模块划分与接口定义

> 接口先于实现。本文档只定义模块边界、职责与对外接口，不包含任何实现细节。  
> **物理原则**：服务端与客户端分属不同目录，仅 `protocol/` 为共享契约层。

---

## 物理目录结构

```
e:/IMSystem/
│
├── protocol/                          # 跨端共享契约层（语言无关）
│   └── src/main/proto/
│       ├── message.proto
│       ├── command.proto
│       └── packet_header.proto
│
├── server/                            # 服务端（Maven 多模块聚合）
│   ├── pom.xml                        # 服务端父 POM
│   ├── im-protocol/                   # Netty 编解码实现（依赖 protocol/ 生成的类）
│   ├── im-common/
│   ├── im-gateway-api/
│   ├── im-gateway/
│   ├── im-session-api/
│   ├── im-session/
│   ├── im-user-api/
│   ├── im-user/
│   ├── im-message-api/
│   ├── im-message/
│   ├── im-group-api/
│   ├── im-group/
│   ├── im-push-api/
│   └── im-push/
│
└── client/                            # 客户端（独立构建，不继承服务端父 POM）
    ├── java-client/                   # Java 桌面/命令行客户端（Maven 独立项目）
    ├── web/                           # HTML/JS Web 聊天页面
    └── android/                       # Android 客户端
```

---

## 一、protocol/（跨端共享契约层）

**定位**：与服务端、客户端完全解耦的独立目录，只包含语言无关的协议定义。  
**构建方式**：通过 `protoc` 编译器分别生成各语言代码（Java、JS、Kotlin、Dart 等）。

**包含内容**：

| 文件 | 说明 |
|---|---|
| `command.proto` | 命令码枚举：LOGIN、HEARTBEAT、SINGLE_MSG、GROUP_MSG、ACK、ERROR |
| `message.proto` | 业务消息体：LoginReq、LoginResp、TextMessage、MessageAck 等 |
| `packet_header.proto` | 协议帧头部结构：magic(2B)、version(1B)、cmd(1B)、len(4B) |

**约束**：
- **禁止**包含任何框架代码（Netty、Spring、Android SDK）。
- **禁止**包含任何逻辑代码（工具类、校验方法）。
- 修改后必须同步到所有客户端，保持版本一致。

---

## 二、服务端内部依赖关系

```
                              protocol/
                                 │
                    ┌────────────┴────────────┐
                    │                         │
               ┌────▼─────┐            ┌──────▼──────┐
               │im-common │            │im-protocol  │
               └────┬─────┘            │(Netty编解码)│
                    │                  └──────┬──────┘
        ┌───────────┼────────────┐            │
        │           │            │            │
   ┌────▼────┐ ┌────▼─────┐ ┌────▼──────┐    │
   │im-user- │ │im-session│ │im-gateway-│    │
   │  api    │ │  -api    │ │   api     │    │
   └────┬────┘ └────┬─────┘ └─────┬─────┘    │
        │           │             │          │
   ┌────▼────┐ ┌────▼─────┐ ┌────▼──────┐   │
   │ im-user │ │im-session│ │im-gateway │◄──┘
   └─────────┘ └──────────┘ └───────────┘
                                   │
        ┌──────────────────────────┼──────────────────────────┐
        │                          │                          │
   ┌────▼─────┐            ┌───────▼────────┐         ┌───────▼──────┐
   │im-message│            │   im-group     │         │   im-push    │
   │  -api    │            │   -api         │         │   -api       │
   └────┬─────┘            └───────┬────────┘         └───────┬──────┘
        │                          │                          │
   ┌────▼─────┐            ┌───────▼────────┐         ┌───────▼──────┐
   │im-message│            │   im-group     │         │   im-push    │
   └──────────┘            └────────────────┘         └──────────────┘
```

**分层原则**：
- `*-api` 模块只定义接口与 DTO，不依赖任何实现框架。
- 实现模块依赖对应的 `*-api`，并通过依赖注入暴露服务。
- `im-common` 被所有服务端模块依赖，禁止反向依赖。
- `im-gateway` 是顶层入口，不依赖任何业务实现，只依赖 `*-api`。

---

## 服务端模块详细定义

### 2.1 im-common（公共基础模块）

**职责**：
- 全局错误码枚举、基础异常类（`IMException`）
- 通用 Result 包装器（`Result<T>`）
- 通用工具类（ID 生成、时间格式化、校验工具）

**对外暴露**：
- 无服务接口，只暴露常量与数据结构。

**依赖**：
- 外部：`protobuf-java`（仅引用生成的 Message 类，不含 .proto 文件）

---

### 2.2 im-protocol（服务端协议编解码实现）

**职责**：
- 基于 `protocol/` 生成的 Java 类，实现 Netty `ChannelHandler` 级别的编解码器
- 封装 `LengthFieldBasedFrameDecoder` 解决 TCP 粘包/拆包
- 将 Protobuf 反序列化后的业务对象交给上层 Handler

**对外暴露的接口**：

```java
package com.im.protocol.codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;

/**
 * 协议编码器：将业务消息对象编码为二进制帧写入 ByteBuf
 */
public interface PacketEncoder {
    void encode(ChannelHandlerContext ctx, Object msg, ByteBuf out) throws Exception;
}

/**
 * 协议解码器：从 ByteBuf 中解析出完整的协议帧并反序列化为业务消息
 */
public interface PacketDecoder {
    Object decode(ChannelHandlerContext ctx, ByteBuf in) throws Exception;
}
```

**依赖**：
- `protocol/`（生成的 Java 类）
- `im-common`
- 外部：`netty-buffer`, `netty-transport`, `netty-codec`

---

### 2.3 im-gateway-api（网关服务接口模块）

**职责**：
- 定义网关暴露给**业务服务**的远程调用接口。
- 业务服务需要主动推消息给用户时，通过此接口调用网关。

**对外暴露的接口**：

```java
package com.im.gateway.api;

import com.im.common.proto.MessagePacket;

/**
 * 网关推送服务
 */
public interface GatewayPushService {

    /**
     * 向指定用户推送消息
     */
    void pushToUser(Long userId, MessagePacket packet);

    /**
     * 向指定群的所有在线成员广播消息
     */
    void pushToGroup(Long groupId, MessagePacket packet);

    /**
     * 向所有在线用户广播（系统通知等）
     */
    void broadcast(MessagePacket packet);

    /**
     * 查询用户是否在线
     */
    boolean isOnline(Long userId);

    /**
     * 强制断开用户所有连接
     */
    void disconnect(Long userId);
}
```

**依赖**：
- `protocol/`（生成的 Java 类）
- `im-common`

---

### 2.4 im-gateway（网关实现模块）

**职责**：
- TCP Server 生命周期管理（启动、停止、端口绑定）
- 新连接接入与 Channel 注册
- 协议帧解析与路由分发
- 心跳维护与空闲连接清理
- 连接层鉴权（Token 校验）
- 实现 `GatewayPushService`，维护 `userId -> Channel` 映射

**对外暴露**：
- 无对外服务接口。对外表现为一个监听 TCP 端口的网络服务。
- 内部实现 `GatewayPushService` 供其他模块注入调用。

**依赖**：
- `protocol/`
- `im-common`
- `im-protocol`
- `im-gateway-api`
- `im-user-api`（用于连接时鉴权）
- `im-session-api`（用于会话绑定）
- 外部：`netty-all`

---

### 2.5 im-session-api（会话管理服务接口模块）

**职责**：
- 定义用户会话生命周期管理的抽象。
- 支持多端登录（一个用户可在多个设备同时在线）。

**对外暴露的接口**：

```java
package com.im.session.api;

import java.util.List;

/**
 * 会话管理服务
 */
public interface SessionService {

    /**
     * 将用户与会话绑定（用户上线时调用）
     */
    void bind(Long userId, String sessionId, ChannelMetadata channel);

    /**
     * 解绑会话（用户断开连接时调用）
     */
    void unbind(String sessionId);

    /**
     * 获取用户的所有活跃会话（多端场景）
     */
    List<SessionInfo> getSessions(Long userId);

    /**
     * 判断用户当前是否有活跃会话
     */
    boolean isOnline(Long userId);

    /**
     * 获取全局在线人数
     */
    long getOnlineCount();
}

/**
 * Channel 元数据（避免依赖 Netty 的 Channel 到 API 层）
 */
public record ChannelMetadata(String sessionId, String host, int port) {}

/**
 * 会话信息
 */
public record SessionInfo(String sessionId, Long userId, long bindTime) {}
```

**依赖**：
- `im-common`

---

### 2.6 im-session（会话管理实现模块）

**职责**：
- 用户在线状态缓存（基于内存 ConcurrentHashMap，可扩展为 Redis）
- 多端登录冲突检测与处理（同类型设备互踢策略）
- Session 超时自动清理

**对外暴露**：
- 实现 `SessionService`

**依赖**：
- `im-common`
- `im-session-api`

---

### 2.7 im-user-api（用户服务接口模块）

**职责**：
- 定义用户领域、认证领域、好友关系领域的抽象接口。

**对外暴露的接口**：

```java
package com.im.user.api;

import java.util.List;

/**
 * 认证服务
 */
public interface AuthService {

    AuthResult register(RegisterRequest request);

    AuthResult login(LoginRequest request);

    /**
     * 校验 Token 是否有效
     */
    boolean verifyToken(String token);

    /**
     * 从 Token 中提取用户ID
     */
    Long extractUserId(String token);
}

/**
 * 用户查询服务
 */
public interface UserQueryService {

    UserInfo getUserById(Long userId);

    List<UserInfo> getUsersByIds(List<Long> userIds);

    boolean exists(Long userId);
}

/**
 * 好友关系服务
 */
public interface FriendService {

    List<Long> getFriendIds(Long userId);

    boolean isFriend(Long userId, Long friendId);

    void addFriend(Long userId, Long friendId);

    void removeFriend(Long userId, Long friendId);
}
```

**依赖**：
- `im-common`

---

### 2.8 im-user（用户服务实现模块）

**职责**：
- 用户注册/登录、密码加密存储
- JWT Token 签发与校验
- 用户资料管理
- 好友关系维护

**对外暴露**：
- 实现 `AuthService`, `UserQueryService`, `FriendService`

**依赖**：
- `im-common`
- `im-user-api`

---

### 2.9 im-message-api（消息服务接口模块）

**职责**：
- 定义消息存储、查询、状态管理的抽象。
- 定义离线消息队列的抽象。

**对外暴露的接口**：

```java
package com.im.message.api;

import java.util.List;

/**
 * 消息核心服务
 */
public interface MessageService {

    /**
     * 保存消息并分配全局消息ID
     */
    Message saveMessage(Message message);

    /**
     * 查询点对点历史消息（双向，按时间倒序）
     */
    List<Message> queryHistory(Long userId, Long peerId, int page, int size);

    /**
     * 标记消息为已读
     */
    void markAsRead(Long userId, Long messageId);

    /**
     * 获取用户总未读消息数
     */
    int getUnreadCount(Long userId);

    /**
     * 获取指定会话的未读消息数
     */
    int getUnreadCount(Long userId, Long peerId);
}

/**
 * 离线消息服务
 */
public interface OfflineMessageService {

    /**
     * 为用户存储离线消息
     */
    void storeOffline(Long userId, Message message);

    /**
     * 用户上线时拉取全部离线消息
     */
    List<Message> pullOffline(Long userId);

    /**
     * 删除已送达的离线消息
     */
    void removeDelivered(Long userId, List<Long> messageIds);
}
```

**依赖**：
- `im-common`

---

### 2.10 im-message（消息服务实现模块）

**职责**：
- 消息持久化到数据库（MySQL）
- 历史消息分页查询与索引优化
- 离线消息队列管理（MySQL 或 Redis List）
- 消息已读/未读状态维护

**对外暴露**：
- 实现 `MessageService`, `OfflineMessageService`

**依赖**：
- `im-common`
- `im-message-api`

---

### 2.11 im-group-api（群组服务接口模块）

**职责**：
- 定义群组领域、群成员领域、群消息扩散策略的抽象。

**对外暴露的接口**：

```java
package com.im.group.api;

import java.util.List;

/**
 * 群核心服务
 */
public interface GroupService {

    GroupInfo createGroup(Long creatorId, CreateGroupRequest request);

    void dissolveGroup(Long groupId, Long operatorId);

    GroupInfo getGroupInfo(Long groupId);

    boolean exists(Long groupId);
}

/**
 * 群成员服务
 */
public interface GroupMemberService {

    void joinGroup(Long groupId, Long userId);

    void leaveGroup(Long groupId, Long userId);

    void kickMember(Long groupId, Long operatorId, Long targetUserId);

    List<Long> getMemberIds(Long groupId);

    boolean isMember(Long groupId, Long userId);

    int getMemberCount(Long groupId);
}

/**
 * 群消息扩散策略
 * 根据群规模决定使用写扩散还是读扩散，返回需要接收此消息的成员ID列表
 */
public interface GroupMessageDistributor {

    /**
     * 分发群消息，返回需要接收该消息的成员ID列表
     */
    List<Long> distribute(Long groupId, Long senderId);
}
```

**依赖**：
- `im-common`

---

### 2.12 im-group（群组服务实现模块）

**职责**：
- 群资料与元数据管理
- 群成员增删查（支持大群场景下的分页查询）
- **群消息扩散策略决策**：
  - 小群（≤200人）：返回全部成员ID，由调用方做写扩散
  - 大群（>200人）：返回空列表（或仅返回在线成员），由调用方走读扩散

**对外暴露**：
- 实现 `GroupService`, `GroupMemberService`, `GroupMessageDistributor`

**依赖**：
- `im-common`
- `im-group-api`
- `im-message-api`（群消息落盘）
- `im-user-api`（校验用户是否存在）

---

### 2.13 im-push-api（推送服务接口模块）

**职责**：
- 定义消息推送的抽象，屏蔽在线推送与离线推送的差异。

**对外暴露的接口**：

```java
package com.im.push.api;

import java.util.List;

/**
 * 统一推送服务
 */
public interface PushService {

    /**
     * 推送单聊消息
     */
    void pushSingle(Long receiverId, Message message);

    /**
     * 推送群消息（传入已决策好的接收者列表）
     */
    void pushGroup(Long groupId, Message message, List<Long> receiverIds);

    /**
     * 推送系统通知给指定用户
     */
    void pushSystemNotification(Long userId, Message message);

    /**
     * 发送离线推送通知（APP 被杀死时走厂商通道）
     */
    void pushOffline(Long userId, Message message);
}
```

**依赖**：
- `im-common`

---

### 2.14 im-push（推送服务实现模块）

**职责**：
- **在线推送**：通过注入 `GatewayPushService`，调用网关将消息实时推送到用户长连接。
- **离线推送**：用户不在线时，调用第三方推送 SDK（APNs/FCM/小米/华为）发送通知。
- 推送失败重试、批量推送合并优化。

**对外暴露**：
- 实现 `PushService`

**依赖**：
- `im-common`
- `im-push-api`
- `im-session-api`（判断用户是否在线，决定走在线推送还是离线推送）
- `im-gateway-api`（在线推送时调用网关）

---

## 三、客户端（client/）

### 说明

客户端是**独立于 server/ 的构建单元**，不继承 server 的父 POM，不依赖任何服务端实现模块。  
客户端只通过 `protocol/` 获取协议定义，自建连接管理、消息收发、UI 层。

### 客户端公共职责

无论哪种技术栈，客户端都必须实现：
1. **TCP 连接管理**：建连、断线重连、指数退避
2. **协议帧编解码**：基于 `protocol/` 生成的代码，实现二进制帧的序列化/反序列化
3. **心跳维护**：定时发送心跳包，检测服务端超时断开
4. **消息 ACK**：发送消息后等待服务端 ACK，超时重传
5. **状态机管理**：连接状态（未连接 / 连接中 / 已连接 / 已登录）
6. **UI 事件绑定**：将接收到的消息回调给 UI 层

### 3.1 client/java-client（Java 桌面/命令行客户端）

**技术栈**：Java 17 + Netty  
**构建工具**：独立 Maven 项目（pom.xml 在 `client/java-client/` 下）

**对外暴露的接口**：

```java
package com.im.client;

/**
 * IM 客户端门面
 */
public interface IMClient {

    /**
     * 建立 TCP 连接并登录
     */
    void connect(String host, int port, String token);

    /**
     * 断开连接
     */
    void disconnect();

    /**
     * 发送单聊消息
     */
    void sendSingleMessage(Long receiverId, String content);

    /**
     * 发送群消息
     */
    void sendGroupMessage(Long groupId, String content);

    /**
     * 注册消息监听器
     */
    void addMessageListener(MessageListener listener);

    /**
     * 注册连接状态监听器
     */
    void addConnectionListener(ConnectionListener listener);
}

public interface MessageListener {
    void onMessageReceived(Message message);
    void onMessageAck(String messageId, int status);
}

public interface ConnectionListener {
    void onConnected();
    void onDisconnected();
    void onReconnecting(int attempt);
}
```

**依赖**：
- `protocol/`（生成的 Java 类）
- 外部：`netty-all`

---

### 3.2 client/web（Web 聊天页面）

**技术栈**：HTML5 + JavaScript（浏览器原生 WebSocket API 或 TCP via WebAssembly）  
**构建工具**：无（纯静态文件，可用 Live Server 打开）

**说明**：
- 浏览器原生不支持裸 TCP，MVP 阶段可用 WebSocket。
- 如果使用 Protobuf，需引入 `protobuf.js` 库做序列化。
- WebSocket 的帧格式与 TCP 二进制协议不同，需额外适配层。

---

### 3.3 client/android（Android 客户端）

**技术栈**：Kotlin + OkHttp / 自研 TCP 连接层  
**构建工具**：Gradle

**说明**：
- 可使用 Kotlin Multiplatform 共享部分逻辑（如协议编解码、状态机）。
- TCP 连接需处理 Android Doze 模式、后台限制、厂商推送通道集成。

---

## 四、接口设计原则

1. **API 层无实现依赖**：所有 `*-api` 模块只包含接口与 DTO，不依赖 Spring、Netty、MySQL 等实现框架。
2. **单向依赖**：依赖图必须是无环的。`im-user` 不依赖 `im-message`，反之亦然；它们通过各自的 `*-api` 解耦。
3. **网关是中心但不是核心**：业务模块不直接依赖 `im-gateway`，而是依赖 `im-gateway-api`。未来如果网关拆分为独立进程，只需把 `GatewayPushService` 改为 RPC 调用即可。
4. **领域边界清晰**：用户、消息、群组、会话、推送五个领域各自独立，跨领域调用通过 `*-api` 接口完成。
5. **协议层物理隔离**：`protocol/` 作为独立目录，被服务端和客户端同时引用，确保通信契约的一致性。
