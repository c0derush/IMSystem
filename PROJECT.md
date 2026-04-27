# IM System - 项目文档

> 本文档记录项目结构、已完成的工作、API 接口和已知问题，供后续开发者/Agent 参考。
> 最后更新: 2026-04-27
> 维护者邮箱: thmfight@outlook.com

---

## 1. 项目概述

这是一个基于 **Java + Spring Boot + Netty + MySQL** 的即时通讯(IM)系统，采用模块化微服务架构（当前为单进程聚合部署）。

### 核心功能
- **用户认证**: 注册、登录、JWT Token 管理
- **私聊**: 一对一消息发送、历史记录查询、SSE 实时推送
- **群聊**: 创建群、加入/退出群、解散群、群消息广播
- **好友系统**: 添加好友、删除好友、好友列表查询
- **消息推送**: SSE (Server-Sent Events) 推送 + Netty TCP 推送 + 离线消息存储
- **Web 前端**: 单页应用，类 QQ 三栏布局（导航 + 列表 + 聊天窗口）

### 技术栈
| 层 | 技术 |
|----|------|
| 后端框架 | Spring Boot 3.2.0, Spring Security, Spring Data JPA |
| 网络通信 | Netty 4.1.101 (TCP), Spring MVC (HTTP), SSE |
| 数据库 | MySQL 8, HikariCP |
| 序列化 | Protobuf |
| 安全 | BCrypt 密码加密, JWT Token (jjwt) |
| 构建工具 | Maven |
| 前端 | 原生 HTML/CSS/JS (单文件 static/index.html) |

---

## 2. 项目结构

```
IMSystem/
├── client/
│   └── java-client/          # Java Netty 客户端示例
├── protocol/
│   └── ...                   # Protobuf 协议定义
├── server/
│   ├── pom.xml               # 服务端父 POM
│   ├── im-common/            # 公共工具类 (SnowflakeId 等)
│   ├── im-protocol/          # Protobuf 编解码
│   ├── im-gateway/           # HTTP网关 + Netty TCP服务器 (主入口)
│   ├── im-gateway-api/       # 网关对外接口
│   ├── im-user/              # 用户服务实现 (Auth, Friend, UserQuery)
│   ├── im-user-api/          # 用户服务接口 + DTO
│   ├── im-message/           # 消息服务实现 (Message, OfflineMessage)
│   ├── im-message-api/       # 消息服务接口 + DTO
│   ├── im-session/           # 会话服务 (在线状态管理)
│   ├── im-session-api/       # 会话服务接口
│   ├── im-push/              # 推送服务实现
│   ├── im-push-api/          # 推送服务接口
│   ├── im-group/             # 群组服务实现
│   └── im-group-api/        # 群组服务接口
├── gateway.log               # 网关运行日志 (可删除)
├── interfaces.md             # 原始接口设计文档
├── IMSystem.md               # 项目简介
├── pom.xml                   # 根 POM
└── PROJECT.md               # 本文档
```

### 模块依赖关系
```
im-gateway (主入口)
  ├── im-common
  ├── im-user-api + im-user (impl)
  ├── im-message-api + im-message (impl)
  ├── im-session-api + im-session (impl)
  ├── im-push-api + im-push (impl)
  ├── im-group-api + im-group (impl)
  └── im-protocol
```

> 注意: 当前并非真正的微服务，而是将各模块作为依赖聚合到 `im-gateway` 单进程中运行。

---

## 3. 核心配置

### 数据库配置
文件: `server/im-gateway/src/main/resources/application.yml`

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/im_system
    username: root
    password: BUPThappy7
  jpa:
    hibernate:
      ddl-auto: update
```

> `ddl-auto: update` 会自动根据 Entity 创建/更新表结构。生产环境建议改为 `validate` 或 `none`。

### 端口
- HTTP API: `8080`
- Netty TCP: `8081`

---

## 4. HTTP API 接口清单

### 4.1 认证接口 (AuthController)

| 方法 | 路径 | 请求体 | 响应 |
|------|------|--------|------|
| POST | `/api/auth/register` | `{username, password, nickname}` | `{success, userId, token, nickname}` |
| POST | `/api/auth/login` | `{username, password}` | `{success, userId, token, nickname}` |

### 4.2 用户接口 (UserController)

| 方法 | 路径 | 参数 | 响应 |
|------|------|------|------|
| GET | `/api/users/{userId}` | - | `UserInfo {userId, username, nickname}` |

### 4.3 好友接口 (FriendController)

| 方法 | 路径 | 参数/请求体 | 响应 |
|------|------|-------------|------|
| GET | `/api/friends` | `?userId=` | `List<UserInfo>` |
| POST | `/api/friends` | `{userId, friendId}` | `{success}` |
| DELETE | `/api/friends/{friendId}` | `?userId=` | `{success}` |

### 4.4 消息接口 (MessageController)

| 方法 | 路径 | 请求体/参数 | 响应 |
|------|------|-------------|------|
| POST | `/api/messages/private` | `{senderId, receiverId, content}` | `{success, messageId}` |
| GET | `/api/messages/private/{peerId}` | `?userId=&page=&size=` | `List<Message>` |
| POST | `/api/messages/group` | `{senderId, groupId, content}` | `{success, messageId, receiverCount}` |
| GET | `/api/messages/group/{groupId}` | `?page=&size=` | `List<Message>` |

### 4.5 群组接口 (GroupController)

| 方法 | 路径 | 请求体/参数 | 响应 |
|------|------|-------------|------|
| POST | `/api/groups` | `{creatorId, name}` | `{groupId, name}` |
| GET | `/api/groups/{groupId}` | - | `GroupInfo` |
| POST | `/api/groups/{groupId}/members` | `{userId}` | - |
| DELETE | `/api/groups/{groupId}/members/{userId}` | - | - |
| DELETE | `/api/groups/{groupId}/members/{targetUserId}/kick` | `?operatorId=` | - |
| DELETE | `/api/groups/{groupId}` | `?operatorId=` | - |
| GET | `/api/groups/{groupId}/members` | - | `List<Long>` |

### 4.6 SSE 接口 (SseController)

| 方法 | 路径 | 参数 | 说明 |
|------|------|------|------|
| GET | `/api/sse/subscribe` | `?userId=` | 建立 SSE 长连接，接收实时消息 |

SSE 事件格式:
```json
{"type": "privateMsg", "messageId": 1, "senderId": 1, "receiverId": 2, "content": "...", "timestamp": 1234567890}
{"type": "groupMsg", "messageId": 1, "senderId": 1, "groupId": 1, "content": "...", "timestamp": 1234567890}
{"type": "groupNotice", "groupId": 1, "noticeType": 1, "targetUserId": 2}
```

### 4.7 Message DTO 结构

```java
public record Message(
    Long messageId,      // 消息ID (Snowflake)
    Long senderId,       // 发送者ID
    Long receiverId,     // 接收者ID (私聊) 或 0 (群聊)
    Long groupId,        // 群ID (群聊) 或 0 (私聊)
    String content,      // 内容
    Long timestamp,      // 毫秒时间戳
    boolean read,        // 是否已读
    String clientMessageId  // 客户端消息ID (去重用，可为null)
) {}
```

---

## 5. 前端说明

### 5.1 文件位置
`server/im-gateway/src/main/resources/static/index.html`

这是一个单文件应用，包含完整的 CSS 和 JavaScript，无需构建工具。

### 5.2 界面布局
- **左侧导航栏** (60px): 用户头像、消息/联系人切换图标、退出按钮
- **中间列表面板** (300px):
  - 消息模式: 会话列表（私聊+群聊），显示未读数、最后消息预览
  - 联系人模式: 好友列表 + 群列表，支持添加/删除
- **右侧聊天区域**:
  - 顶部: 聊天对象名称和类型标签
  - 中间: 消息气泡（绿色=自己，白色=对方，灰色=系统）
  - 底部: 输入框 + 发送按钮（支持回车发送）

### 5.3 前端状态
```javascript
conversations = [{ id, type, targetId, name, avatarText, lastMessage, lastTime, unread, messages: [] }]
friends = [UserInfo]
groups = [{ groupId, name }]
userCache = {}  // 用户ID -> 用户信息缓存
```

---

## 6. 已完成的工作记录

### 6.1 Bug 修复

#### (1) Spring Security CSRF 导致 POST 400/403
- **问题**: 旧版本 Spring Security 默认启用 CSRF，导致前端 POST 请求被拒绝
- **修复**: `SecurityConfig.java` 中禁用 CSRF
  ```java
  .csrf(AbstractHttpConfigurer::disable)
  .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
  ```

#### (2) `@RequestParam` / `@PathVariable` 参数名丢失导致 500
- **问题**: Maven 未启用 `-parameters` 编译标志，Spring 无法从 class 文件读取参数名
- **修复**:
  - 为所有 Controller 的注解添加显式名称: `@RequestParam("userId")`, `@PathVariable("groupId")` 等
  - 根 `pom.xml` 中全局启用 `<parameters>true</parameters>` 防止复发

#### (3) `ChatMessage.sentAt` 为空导致 500
- **问题**: `MessageServiceImpl` 手动设置 Snowflake ID 导致 `save()` 调用 `merge()`，`@PrePersist` 未被触发
- **修复**: 在 `saveMessage()` 中显式设置 `entity.setSentAt(LocalDateTime.now())`

#### (4) `clientMessageId` 空字符串导致唯一索引冲突
- **问题**: 多条消息的 `clientMessageId` 均为 `""`，触发 MySQL 唯一索引 `Duplicate entry ''`
- **修复**: `MessageServiceImpl.saveMessage()` 中当 `clientMessageId` 为空时，不设置到实体上（保持 null）

### 6.2 功能增强

#### (1) 新增好友管理接口
- 文件: `FriendController.java`
- 接口: `GET /api/friends`, `POST /api/friends`, `DELETE /api/friends/{friendId}`

#### (2) 新增用户查询接口
- 文件: `UserController.java`
- 接口: `GET /api/users/{userId}`

#### (3) 新增私聊消息接口
- 文件: `MessageController.java`
- 接口: `POST /api/messages/private`, `GET /api/messages/private/{peerId}`
- 推送: 同时调用 `pushService.pushSingle()` + `sseMessagePusher.pushToUser()` 进行双通道推送

#### (4) 前端从群聊测试页升级为类 QQ 完整 IM 界面
- 重写 `index.html`，新增:
  - 三栏布局（导航 + 列表 + 聊天）
  - 私聊/群聊会话管理
  - 好友添加/删除
  - 群创建/加入/退出/解散
  - 未读消息红点
  - 消息气泡和时间显示

### 6.3 数据库变更
- 所有用户密码统一更新为 `123456`（BCrypt 加密存储）

---

## 7. 已知问题与限制

### 7.1 好友数据非持久化
- `FriendServiceImpl` 使用内存 `ConcurrentHashMap` 存储好友关系
- **重启服务器后好友列表会丢失**
- 建议: 迁移到数据库表（如 `friendships`）

### 7.2 群列表非持久化（前端视角）
- 后端没有 `GET /api/groups/my` 接口
- 前端刷新页面后，已加入的群不会自动恢复（需要从会话历史或重新加入）
- 建议: 后端提供"查询我加入的群"接口

### 7.3 `groups` 表名曾为 MySQL 保留字
- 旧日志中有 `create table groups` 失败的记录
- 当前 `Group` 实体已改为 `@Table(name = "im_group")`，但需确认数据库中是否还有旧表

### 7.4 `FriendServiceImpl` 缺少 `@Transactional` 等保护
- 当前为内存实现，并发安全但无持久化保证

### 7.5 消息分页未做前端加载更多
- 前端每次加载历史消息只拉取第一页（默认50条）
- 建议: 聊天窗口滚动到顶部时自动加载更多

### 7.6 头像为默认文字头像
- 当前使用昵称首字母作为头像，无真实头像上传功能

---

## 8. 如何运行

### 8.1 环境要求
- Java 17+
- Maven 3.8+
- MySQL 8.0+

### 8.2 创建数据库
```sql
CREATE DATABASE IF NOT EXISTS im_system CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

### 8.3 启动服务端
```bash
cd server/im-gateway
mvn spring-boot:run
```

或编译后启动:
```bash
cd server/im-gateway
mvn clean compile
mvn spring-boot:run
```

### 8.4 访问前端
浏览器打开: `http://localhost:8080`

### 8.5 默认账号
当前数据库中有 2 个用户，密码均为 `123456`:
- `USER1` / `123456` (ID: 1, 昵称: Alice)
- `USER2` / `123456` (ID: 2, 昵称: Bob)

---

## 9. 后续建议

### 高优先级
1. **好友数据持久化**: 将 `FriendServiceImpl` 的 `ConcurrentHashMap` 改为 JPA 数据库存储
2. **群成员查询接口**: 提供 `GET /api/groups/my?userId=` 查询当前用户加入的所有群
3. **用户搜索接口**: 支持按用户名/昵称模糊搜索用户

### 中优先级
4. **头像上传**: 支持用户上传头像文件（本地存储或 OSS）
5. **消息已读回执**: 私聊消息点击后自动标记已读
6. **消息加载更多**: 前端滚动加载历史消息分页

### 低优先级
7. **WebSocket 替代 SSE**: SSE 在部分网络环境下不稳定，可考虑 WebSocket
8. **真正的微服务拆分**: 当前为单进程聚合，可按模块拆分为独立服务 + Nacos/Eureka 注册中心
9. **Spring Cloud Gateway**: 使用真正的网关做路由转发，而非当前的单体聚合模式

---

## 10. 关键文件索引

| 文件 | 说明 |
|------|------|
| `server/im-gateway/src/main/java/com/im/gateway/config/SecurityConfig.java` | Spring Security 配置 |
| `server/im-gateway/src/main/java/com/im/gateway/controller/AuthController.java` | 认证接口 |
| `server/im-gateway/src/main/java/com/im/gateway/controller/FriendController.java` | 好友接口 |
| `server/im-gateway/src/main/java/com/im/gateway/controller/UserController.java` | 用户查询接口 |
| `server/im-gateway/src/main/java/com/im/gateway/controller/MessageController.java` | 消息接口（私聊+群聊） |
| `server/im-gateway/src/main/java/com/im/gateway/controller/GroupController.java` | 群组接口 |
| `server/im-gateway/src/main/java/com/im/gateway/controller/SseController.java` | SSE 订阅接口 |
| `server/im-gateway/src/main/resources/static/index.html` | Web 前端 |
| `server/im-message/src/main/java/com/im/message/service/MessageServiceImpl.java` | 消息保存/查询逻辑 |
| `server/im-user/src/main/java/com/im/user/service/FriendServiceImpl.java` | 好友逻辑（内存实现） |
| `server/im-user/src/main/java/com/im/user/service/AuthServiceImpl.java` | 认证逻辑 |
| `pom.xml` | 根 POM（含 `-parameters` 编译器配置） |

---

*如有疑问，请联系 thmfight@outlook.com*
