# 备考答疑系统 - 项目优化方案

## 一、用户登录预留设计

### 1.1 用户表结构

**sys_user表** - 用户基础信息
```sql
CREATE TABLE sys_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(50) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    email VARCHAR(100) UNIQUE NOT NULL,
    phone VARCHAR(20),
    avatar_url VARCHAR(255),
    is_active BOOLEAN DEFAULT TRUE,
    created_at DATETIME,
    updated_at DATETIME
);
```

**核心字段说明**：
- `id`: 用户唯一标识，所有数据隔离的核心键
- `username`: 登录账号
- `password`: BCrypt加密后的密码
- `email`: 邮箱，用于找回密码等
- `is_active`: 账号状态，支持禁用用户

### 1.2 登录鉴权接口

**注册接口** - `POST /api/auth/register`
```java
// 请求体
{
    "username": "student001",
    "password": "password123",
    "email": "student@example.com",
    "phone": "13800138000"
}

// 响应
{
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "userId": 1,
    "username": "student001",
    "message": "注册成功"
}
```

**登录接口** - `POST /api/auth/login`
```java
// 请求体
{
    "username": "student001",
    "password": "password123"
}

// 响应
{
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "userId": 1,
    "username": "student001",
    "message": "登录成功"
}
```

**Token验证接口** - `POST /api/auth/validate`
```java
// 请求头
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...

// 响应
true/false
```

### 1.3 基于用户ID的请求拦截/数据隔离规则

**拦截器实现** - [AuthInterceptor.java](src/main/java/com/syh/chat/interceptor/AuthInterceptor.java)
- 所有`/api/**`请求（除`/api/auth/**`）必须携带`Authorization: Bearer {token}`
- 拦截器验证Token有效性，提取`userId`存入Request属性
- Controller通过`getUserId(request)`获取当前用户ID

**数据隔离规则**：
1. **内存层**：`ConcurrentHashMap<Long, ConcurrentHashMap<String, List<Message>>>`
   - 第一层Key：用户ID
   - 第二层Key：会话ID
   - 确保用户只能访问自己的内存数据

2. **Redis层**：Key设计`user:conversation:{userId}:{sessionId}`
   - 每个用户的对话数据独立存储
   - 支持按用户查询所有会话：`user:session:{userId}`

3. **MySQL层**：所有表包含`user_id`字段
   - `conversation`表：`user_id` + `session_id`唯一索引
   - `message`表：`user_id` + `conversation_id`关联查询
   - Repository层自动过滤用户数据

---

## 二、当前内存阶段的对话功能优化

### 2.1 用户ID维度的会话隔离

**核心服务** - [UserConversationService.java](src/main/java/com/syh/chat/service/UserConversationService.java)

**数据结构**：
```java
ConcurrentHashMap<Long, ConcurrentHashMap<String, List<Message>>> userConversations
```

**隔离逻辑**：
```java
// 获取用户专属对话
public List<Message> getConversation(Long userId, String sessionId) {
    return userConversations
        .computeIfAbsent(userId, k -> new ConcurrentHashMap<>())
        .computeIfAbsent(sessionId, k -> new ArrayList<>());
}
```

### 2.2 对话管理功能实现

**1. 发起新对话**
```java
// POST /api/conversation/new
String sessionId = userConversationService.createNewConversation(userId);
// 返回新的sessionId
```

**2. 接续指定历史对话**
```java
// POST /api/conversation/continue
// 请求体包含sessionId
// 系统从内存/Redis/MySQL加载该会话历史
```

**3. 选择对话片段作为上下文**
```java
// POST /api/conversation/fragment
{
    "sessionId": "xxx",
    "startOrder": 0,
    "endOrder": 5
}
// 返回指定范围的对话片段
```

**4. 删除历史对话**
```java
// DELETE /api/conversation/{sessionId}
// 删除单个会话

// DELETE /api/conversation/{sessionId}/message/{messageOrder}
// 删除单条消息

// DELETE /api/conversation/all
// 删除用户所有会话
```

### 2.3 基于LangChain优化用户专属会话记忆

**LangChain集成** - [UnifiedConversationService.java](src/main/java/com/syh/chat/service/UnifiedConversationService.java)

**核心特性**：
1. **用户专属记忆**：每个用户+会话组合独立记忆
   ```java
   Map<String, ChatMemory> userMemories
   // Key格式: {userId}:{sessionId}
   ```

2. **消息窗口记忆**：保留最近50条消息
   ```java
   MessageWindowChatMemory.withMaxMessages(50)
   ```

3. **记忆管理**：
   - `addMessageToMemory()`: 添加消息到记忆
   - `getConversationHistory()`: 获取历史对话
   - `clearMemory()`: 清空记忆
   - `loadConversationFromHistory()`: 从历史加载

---

## 三、分步接入Redis/MySQL

### 3.1 Redis缓存集成（用户隔离）

**Redis Key设计**：
```
用户对话数据：user:conversation:{userId}:{sessionId}
用户会话列表：user:session:{userId}
```

**核心功能** - [RedisConversationService.java](src/main/java/com/syh/chat/service/RedisConversationService.java)

**用户隔离逻辑**：
```java
private String getUserConversationKey(Long userId, String sessionId) {
    return USER_CONVERSATION_PREFIX + userId + ":" + sessionId;
}

private String getUserSessionsKey(Long userId) {
    return USER_SESSION_PREFIX + userId;
}
```

**数据操作**：
1. **查询**：`getConversation(userId, sessionId)`
2. **保存**：`saveConversation(userId, sessionId, messages)`
3. **添加消息**：`addMessage(userId, sessionId, message)`
4. **删除**：`deleteConversation(userId, sessionId)`
5. **片段查询**：`getMessageFragment(userId, sessionId, startOrder, endOrder)`

**TTL管理**：默认24小时过期，支持续期

### 3.2 MySQL持久化集成（用户隔离）

**表结构设计**：

**conversation表** - 会话信息
```sql
CREATE TABLE conversation (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    session_id VARCHAR(64) UNIQUE NOT NULL,
    title VARCHAR(200),
    model_name VARCHAR(50),
    is_active BOOLEAN DEFAULT TRUE,
    created_at DATETIME,
    updated_at DATETIME,
    INDEX idx_user_id (user_id)
);
```

**message表** - 消息内容
```sql
CREATE TABLE message (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    conversation_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    role VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    image_url VARCHAR(500),
    message_order INT,
    created_at DATETIME,
    INDEX idx_conversation_id (conversation_id),
    INDEX idx_user_id (user_id)
);
```

**核心功能** - [DatabaseConversationService.java](src/main/java/com/syh/chat/service/DatabaseConversationService.java)

**用户隔离逻辑**：
```java
public List<Message> getConversationMessages(Long userId, String sessionId) {
    Optional<Conversation> conversationOpt = conversationRepository.findBySessionId(sessionId);
    if (conversationOpt.isEmpty()) {
        return new ArrayList<>();
    }
    
    Conversation conversation = conversationOpt.get();
    // 关键：验证用户所有权
    if (!conversation.getUserId().equals(userId)) {
        return new ArrayList<>();
    }
    
    // 只返回该用户的消息
    return messageRepository.findByConversationIdOrderByMessageOrderAsc(conversation.getId());
}
```

**数据操作**：
1. **创建会话**：`createConversation(userId, title, modelName)`
2. **保存消息**：`saveMessage(userId, sessionId, message)` - 自动验证用户权限
3. **查询历史**：`getConversationMessages(userId, sessionId)`
4. **片段查询**：`getMessageFragment(userId, sessionId, startOrder, endOrder)`
5. **删除**：`deleteConversation(userId, sessionId)` - 级联删除消息

---

## 四、核心代码示例

### 4.1 用户登录鉴权预留接口

**JWT工具类** - [JwtUtil.java](src/main/java/com/syh/chat/util/JwtUtil.java)
```java
@Component
public class JwtUtil {
    
    @Value("${jwt.secret}")
    private String secret;
    
    @Value("${jwt.expiration}")
    private Long expiration;
    
    public String generateToken(Long userId, String username) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("username", username);
        return createToken(claims, username);
    }
    
    public Long extractUserId(String token) {
        return extractClaims(token).get("userId", Long.class);
    }
    
    public Boolean validateToken(String token, String username) {
        final String extractedUsername = extractUsername(token);
        return (extractedUsername.equals(username) && !isTokenExpired(token));
    }
}
```

**认证服务** - [AuthService.java](src/main/java/com/syh/chat/service/AuthService.java)
```java
@Service
public class AuthService {
    
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElse(null);
        
        if (user == null) {
            return new AuthResponse(null, null, null, "用户不存在");
        }
        
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            return new AuthResponse(null, null, null, "密码错误");
        }
        
        String token = jwtUtil.generateToken(user.getId(), user.getUsername());
        return new AuthResponse(token, user.getId(), user.getUsername(), "登录成功");
    }
}
```

### 4.2 基于用户ID的会话管理工具类

**统一会话服务** - [UnifiedConversationService.java](src/main/java/com/syh/chat/service/UnifiedConversationService.java)

**三层架构集成**：
```java
@Service
public class UnifiedConversationService {
    
    private final UserConversationService userConversationService;      // 内存层
    private final RedisConversationService redisConversationService;    // Redis层
    private final DatabaseConversationService databaseConversationService; // MySQL层
    private final LangChainConversationService langChainConversationService; // LangChain层
    
    // 查询优先级：内存 -> Redis -> MySQL
    public List<Message> getConversation(Long userId, String sessionId) {
        List<Message> conversation = userConversationService.getConversation(userId, sessionId);
        
        if (conversation.isEmpty()) {
            conversation = redisConversationService.getConversation(userId, sessionId);
        }
        
        if (conversation.isEmpty()) {
            conversation = databaseConversationService.getConversationMessages(userId, sessionId);
        }
        
        return conversation;
    }
    
    // 写入时同步到所有层
    public void addMessage(Long userId, String sessionId, Message message) {
        userConversationService.addMessage(userId, sessionId, message);
        redisConversationService.addMessage(userId, sessionId, message);
        databaseConversationService.saveMessage(userId, sessionId, message);
        langChainConversationService.addMessageToMemory(userId, sessionId, message);
    }
}
```

### 4.3 LangChain记忆与用户专属对话的衔接代码

**LangChain集成示例**：
```java
@Service
public class LangChainConversationService {
    
    private final Map<String, ChatMemory> userMemories = new ConcurrentHashMap<>();
    
    private ChatMemory getOrCreateMemory(Long userId, String sessionId) {
        String memoryKey = userId + ":" + sessionId;
        return userMemories.computeIfAbsent(memoryKey, k -> 
            MessageWindowChatMemory.withMaxMessages(50)
        );
    }
    
    public void loadConversationFromHistory(Long userId, String sessionId, List<Message> history) {
        ChatMemory memory = getOrCreateMemory(userId, sessionId);
        
        for (Message message : history) {
            ChatMessage chatMessage = convertToLangChainMessage(message);
            memory.add(chatMessage);
        }
    }
    
    public String chatWithMemory(Long userId, String sessionId, String userMessage, String modelName) {
        ChatMemory memory = getOrCreateMemory(userId, sessionId);
        ChatLanguageModel model = createChatModel(modelName);
        
        memory.add(UserMessage.from(userMessage));
        String response = model.generate(memory.messages()).content().text();
        memory.add(AiMessage.from(response));
        
        return response;
    }
}
```

### 4.4 Redis/MySQL接入的关键接口

**Redis接口**：
```java
// 用户专属Key设计
private String getUserConversationKey(Long userId, String sessionId) {
    return "user:conversation:" + userId + ":" + sessionId;
}

// 保存用户对话
public void saveConversation(Long userId, String sessionId, List<Message> messages) {
    String key = getUserConversationKey(userId, sessionId);
    String json = objectMapper.writeValueAsString(messages);
    redisTemplate.opsForValue().set(key, json, 24, TimeUnit.HOURS);
}

// 查询用户所有会话
public List<String> getUserSessions(Long userId) {
    String sessionsKey = "user:session:" + userId;
    return new ArrayList<>(redisTemplate.opsForSet().members(sessionsKey));
}
```

**MySQL接口**：
```java
// 创建用户专属会话
@Transactional
public String createConversation(Long userId, String title, String modelName) {
    Conversation conversation = new Conversation();
    conversation.setUserId(userId);  // 用户隔离核心
    conversation.setSessionId(UUID.randomUUID().toString());
    conversation.setTitle(title);
    conversation.setModelName(modelName);
    
    conversation = conversationRepository.save(conversation);
    return conversation.getSessionId();
}

// 保存用户消息（带权限验证）
@Transactional
public void saveMessage(Long userId, String sessionId, Message message) {
    Optional<Conversation> conversationOpt = conversationRepository.findBySessionId(sessionId);
    if (conversationOpt.isEmpty()) {
        throw new IllegalArgumentException("对话不存在");
    }
    
    Conversation conversation = conversationOpt.get();
    // 关键：验证用户所有权
    if (!conversation.getUserId().equals(userId)) {
        throw new IllegalArgumentException("无权访问该对话");
    }
    
    MessageEntity messageEntity = new MessageEntity();
    messageEntity.setConversationId(conversation.getId());
    messageEntity.setUserId(userId);  // 用户隔离
    messageEntity.setRole(message.getRole());
    messageEntity.setContent(message.getContent());
    
    messageRepository.save(messageEntity);
}
```

---

## 五、数据流转架构

### 5.1 查询流程
```
用户请求 -> 拦截器验证Token -> 提取userId
    -> 内存层查询
        -> 未命中 -> Redis层查询
            -> 未命中 -> MySQL层查询
                -> 返回数据 -> 同步到内存和Redis
```

### 5.2 写入流程
```
用户发送消息 -> 拦截器验证Token -> 提取userId
    -> 内存层写入
    -> Redis层写入
    -> MySQL层写入
    -> LangChain记忆写入
    -> 返回响应
```

### 5.3 用户隔离保障
1. **拦截器层**：所有请求必须携带有效Token
2. **Service层**：所有操作都基于userId
3. **Repository层**：查询自动过滤userId
4. **Redis Key**：包含userId确保隔离
5. **MySQL表**：userId字段+索引

---

## 六、API接口清单

### 6.1 认证接口
- `POST /api/auth/register` - 用户注册
- `POST /api/auth/login` - 用户登录
- `POST /api/auth/validate` - Token验证

### 6.2 对话管理接口
- `POST /api/conversation/new` - 创建新对话
- `POST /api/conversation/continue` - 接续历史对话
- `POST /api/conversation/fragment` - 选择对话片段
- `GET /api/conversation/list` - 获取用户所有对话
- `GET /api/conversation/{sessionId}` - 获取对话详情
- `DELETE /api/conversation/{sessionId}` - 删除对话
- `DELETE /api/conversation/{sessionId}/message/{order}` - 删除单条消息

### 6.3 统一对话接口
- `POST /api/unified/chat/stream` - 流式对话
- `POST /api/unified/conversation/new` - 创建新对话（统一层）
- `POST /api/unified/conversation/continue` - 接续历史对话（统一层）
- `POST /api/unified/conversation/fragment` - 选择片段继续（统一层）
- `GET /api/unified/conversation/{sessionId}/summary` - 对话摘要
- `POST /api/unified/conversation/{sessionId}/sync/database` - 同步到MySQL
- `POST /api/unified/conversation/{sessionId}/sync/redis` - 同步到Redis

---

## 七、配置说明

### 7.1 数据库配置
```properties
spring.datasource.url=jdbc:mysql://localhost:3306/ollama_chat
spring.datasource.username=root
spring.datasource.password=1234
```

### 7.2 Redis配置
```properties
spring.data.redis.host=localhost
spring.data.redis.port=6379
spring.data.redis.database=0
```

### 7.3 JWT配置
```properties
jwt.secret=your-secret-key-must-be-at-least-256-bits-long
jwt.expiration=86400000  # 24小时
```

---

## 八、后续扩展方向

### 8.1 错题本功能
- 新增`wrong_question`表，关联用户ID
- 支持从对话中标记错题
- 错题分类、复习提醒

### 8.2 学习进度跟踪
- 新增`study_progress`表
- 记录用户学习时长、对话次数
- 生成学习报告

### 8.3 知识点关联
- 新增`knowledge_point`表
- 对话自动提取知识点
- 知识图谱构建

### 8.4 多模态增强
- 支持图片、PDF、Word文档上传
- 文档内容提取与向量化
- 基于文档的问答

---

## 九、技术栈总结

| 组件 | 技术 | 用途 |
|------|------|------|
| 后端框架 | Spring Boot 3.5.9 | Web应用框架 |
| 数据库 | MySQL 8.0 | 持久化存储 |
| 缓存 | Redis 7.x | 会话缓存 |
| 认证 | JWT + Spring Security | 用户鉴权 |
| ORM | Spring Data JPA | 数据库操作 |
| 大模型 | BigModel / SiliconFlow（网络 API） | AI对话 |
| LangChain | LangChain4j | 会话记忆管理 |
| 工具 | Lombok | 简化代码 |

---

## 十、部署说明

### 10.1 环境要求
- JDK 17+
- MySQL 8.0+
- Redis 7.x
- 可选：大模型 API Key（BigModel / SiliconFlow）

### 10.2 启动步骤
1. 创建MySQL数据库：`CREATE DATABASE ollama_chat;`
2. 启动Redis服务
3. 运行应用：`mvn spring-boot:run`

### 10.3 初始化
- 首次启动自动创建表结构
- 注册第一个用户
- 开始使用对话功能

---

## 十一、安全建议

1. **生产环境必须修改JWT密钥**
2. **启用HTTPS**
3. **数据库密码加密存储**
4. **定期备份MySQL数据**
5. **Redis设置密码认证**
6. **限制API调用频率**

---

## 十二、性能优化建议

1. **Redis缓存预热**：系统启动时加载活跃用户会话
2. **数据库索引优化**：userId、sessionId字段建立索引
3. **连接池配置**：合理设置数据库和Redis连接池大小
4. **异步处理**：非关键操作异步执行（如数据归档）
5. **分页查询**：历史对话列表支持分页

---

## 十三、故障排查

### 13.1 常见问题
1. **Token过期**：重新登录获取新Token
2. **Redis连接失败**：检查Redis服务状态
3. **MySQL连接失败**：检查数据库配置和网络
4. **大模型调用失败**：检查 API Key、网络连通性、限流/熔断配置与下游状态

### 13.2 日志查看
```bash
# 查看应用日志
tail -f logs/application.log

# 查看SQL日志
grep "Hibernate" logs/application.log
```

---

## 十四、联系与支持

如有问题，请查看：
- 项目README.md
- API文档（Swagger）
- 技术文档（docs/目录）
