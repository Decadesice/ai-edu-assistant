# 备考答疑系统 - 快速启动指南

## 环境准备

### 1. 必需软件
- **JDK 17+**: [下载地址](https://www.oracle.com/java/technologies/downloads/)
- **MySQL 8.0+**: [下载地址](https://dev.mysql.com/downloads/mysql/)
- **Redis 7.x**: [下载地址](https://redis.io/download)
- **Ollama**: [下载地址](https://ollama.com/)
- **Maven 3.6+**: [下载地址](https://maven.apache.org/download.cgi)

### 2. 安装Ollama模型
```bash
# 安装Ollama后，下载Qwen3模型
ollama pull qwen3-vl:2b
# 或
ollama pull qwen3:8b
```

## 数据库配置

### 1. 创建数据库
```bash
# 登录MySQL
mysql -u root -p

# 执行初始化脚本（在 MySQL 客户端内执行）
source ai-chat/src/main/resources/schema.sql
```

### 2. 验证数据库
```sql
USE ollama_chat;
SHOW TABLES;
-- 应该看到：sys_user, conversation, message
```

## Redis配置

### 1. 启动Redis
```bash
# Windows
redis-server.exe

# Linux/Mac
redis-server
```

### 2. 验证Redis
```bash
redis-cli
> PING
PONG
```

## 应用启动

### 1. 配置检查
确保 `application.properties` 配置正确：
```properties
spring.datasource.url=jdbc:mysql://localhost:3306/ollama_chat
spring.datasource.username=root
spring.datasource.password=1234

spring.data.redis.host=localhost
spring.data.redis.port=6379
```

### 2. 编译项目
```bash
cd ai-chat
mvn clean install
```

### 3. 启动应用
```bash
mvn spring-boot:run
```

### 4. 验证启动（后端）
后端为纯 API 服务，启动后可用下列方式验证：
- `http://localhost:8081/api/auth/login`（POST）
- `http://localhost:8081/api/unified/chat/stream`（POST）

## 前端启动（前后端分离）

前端目录：`ai-chat-frontend`

```bash
cd ai-chat-frontend
npm install
npm run dev
```

浏览器访问 `http://localhost:5174`。

## API测试

### 1. 用户注册
```bash
curl -X POST http://localhost:8081/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "student001",
    "password": "password123",
    "email": "student@example.com"
  }'
```

**响应示例**：
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "userId": 1,
  "username": "student001",
  "message": "注册成功"
}
```

### 2. 用户登录
```bash
curl -X POST http://localhost:8081/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "student001",
    "password": "password123"
  }'
```

### 3. 创建新对话
```bash
curl -X POST http://localhost:8081/api/conversation/new \
  -H "Authorization: Bearer YOUR_TOKEN_HERE"
```

### 4. 发送消息（流式）
```bash
curl -X POST http://localhost:8081/api/unified/chat/stream \
  -H "Authorization: Bearer YOUR_TOKEN_HERE" \
  -H "Content-Type: application/json" \
  -d '{
    "sessionId": "YOUR_SESSION_ID",
    "message": "请解释一下Java中的多线程",
    "model": "qwen3-vl:2b"
  }'
```

### 5. 获取对话历史
```bash
curl -X GET http://localhost:8081/api/unified/conversation/YOUR_SESSION_ID \
  -H "Authorization: Bearer YOUR_TOKEN_HERE"
```

### 6. 获取对话片段
```bash
curl -X GET "http://localhost:8081/api/unified/conversation/YOUR_SESSION_ID/fragment?startOrder=0&endOrder=5" \
  -H "Authorization: Bearer YOUR_TOKEN_HERE"
```

### 7. 删除对话
```bash
curl -X DELETE http://localhost:8081/api/unified/conversation/YOUR_SESSION_ID \
  -H "Authorization: Bearer YOUR_TOKEN_HERE"
```

## 功能演示

### 场景1：发起新对话
```bash
# 1. 创建新对话
curl -X POST http://localhost:8081/api/conversation/new \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"title": "考研数学复习", "modelName": "qwen3-vl:2b"}'

# 返回sessionId，例如：abc123

# 2. 发送消息
curl -X POST http://localhost:8081/api/unified/chat/stream \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "sessionId": "abc123",
    "message": "请帮我总结高等数学中的导数定义",
    "model": "qwen3-vl:2b"
  }'
```

### 场景2：接续历史对话
```bash
# 1. 获取用户所有对话
curl -X GET http://localhost:8081/api/conversation/list \
  -H "Authorization: Bearer YOUR_TOKEN"

# 2. 选择一个对话继续
curl -X POST http://localhost:8081/api/conversation/continue \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"sessionId": "abc123"}'

# 3. 发送新消息
curl -X POST http://localhost:8081/api/unified/chat/stream \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "sessionId": "abc123",
    "message": "能举个例子吗？",
    "model": "qwen3-vl:2b"
  }'
```

### 场景3：选择对话片段继续
```bash
# 1. 获取对话片段（第0-3条消息）
curl -X GET "http://localhost:8081/api/unified/conversation/abc123/fragment?startOrder=0&endOrder=3" \
  -H "Authorization: Bearer YOUR_TOKEN"

# 2. 基于片段继续对话
curl -X POST http://localhost:8081/api/unified/conversation/fragment \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "sessionId": "abc123",
    "startOrder": 0,
    "endOrder": 3
  }'

# 3. 发送新消息（系统会基于片段上下文回答）
curl -X POST http://localhost:8081/api/unified/chat/stream \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "sessionId": "abc123",
    "message": "请基于上述内容详细解释",
    "model": "qwen3-vl:2b"
  }'
```

### 场景4：对话摘要
```bash
curl -X GET "http://localhost:8081/api/unified/conversation/abc123/summary?model=qwen3-vl:2b" \
  -H "Authorization: Bearer YOUR_TOKEN"
```

## 数据同步

### 手动同步到MySQL
```bash
curl -X POST http://localhost:8081/api/unified/conversation/abc123/sync/database \
  -H "Authorization: Bearer YOUR_TOKEN"
```

### 手动同步到Redis
```bash
curl -X POST http://localhost:8081/api/unified/conversation/abc123/sync/redis \
  -H "Authorization: Bearer YOUR_TOKEN"
```

## 常见问题

### 1. Token过期
```bash
# 重新登录获取新Token
curl -X POST http://localhost:8081/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "student001",
    "password": "password123"
  }'
```

### 2. 数据库连接失败
```bash
# 检查MySQL服务状态
# Windows
net start MySQL80

# Linux
sudo systemctl status mysql

# 验证连接
mysql -u root -p -h localhost
```

### 3. Redis连接失败
```bash
# 检查Redis服务状态
redis-cli ping

# 如果返回PONG，说明Redis正常
```

### 4. Ollama无响应
```bash
# 检查Ollama服务
ollama list

# 如果没有模型，下载模型
ollama pull qwen3-vl:2b

# 测试模型
ollama run qwen3-vl:2b
```

## 日志查看

### 应用日志
```bash
# 查看实时日志
tail -f logs/application.log

# 查看错误日志
grep ERROR logs/application.log
```

### SQL日志
```bash
# 查看所有SQL语句
grep "Hibernate" logs/application.log
```

## 性能监控

### 1. 查看Redis缓存
```bash
redis-cli
> KEYS user:conversation:*
> GET user:conversation:1:abc123
```

### 2. 查看MySQL数据
```sql
USE ollama_chat;

-- 查看用户数
SELECT COUNT(*) FROM sys_user;

-- 查看对话数
SELECT COUNT(*) FROM conversation;

-- 查看消息数
SELECT COUNT(*) FROM message;

-- 查看用户对话详情
SELECT c.*, COUNT(m.id) as message_count
FROM conversation c
LEFT JOIN message m ON c.id = m.conversation_id
WHERE c.user_id = 1
GROUP BY c.id;
```

## 开发调试

### 1. 启用调试模式
在 `application.properties` 中设置：
```properties
logging.level.com.ollama.chat=DEBUG
logging.level.org.hibernate.SQL=DEBUG
logging.level.org.hibernate.type.descriptor.sql.BasicBinder=TRACE
```

### 2. 使用IDE调试
1. 在IDE中打开项目
2. 设置断点
3. 以Debug模式启动应用
4. 发送请求触发断点

### 3. 测试数据清理
```bash
# 删除测试用户的所有数据
curl -X DELETE http://localhost:8081/api/conversation/all \
  -H "Authorization: Bearer YOUR_TOKEN"
```

## 生产部署

### 1. 修改配置
```properties
# 修改JWT密钥（必须）
jwt.secret=your-production-secret-key-at-least-256-bits-long

# 修改数据库密码
spring.datasource.password=your-secure-password

# 启用HTTPS
server.ssl.enabled=true
server.ssl.key-store=classpath:keystore.p12
server.ssl.key-store-password=your-keystore-password
```

### 2. 打包应用
```bash
mvn clean package
java -jar target/chat-1.0.0.jar
```

### 3. 使用Docker部署
```dockerfile
FROM openjdk:17-jdk-slim
COPY target/chat-1.0.0.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app.jar"]
```

```bash
docker build -t ai-chat .
docker run -p 8081:8081 ai-chat
```

## 技术支持

如遇问题，请：
1. 查看日志文件
2. 检查配置文件
3. 参考架构文档 [ARCHITECTURE.md](ARCHITECTURE.md)
4. 联系技术支持

## 下一步

- 阅读完整架构文档：[ARCHITECTURE.md](ARCHITECTURE.md)
- 查看API文档：启动后访问 `/swagger-ui.html`
- 开发新功能：参考现有代码结构
- 性能优化：参考架构文档中的性能优化建议
