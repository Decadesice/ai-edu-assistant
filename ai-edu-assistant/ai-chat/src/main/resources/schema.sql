-- 备考答疑系统 - 数据库初始化脚本
-- 创建数据库
CREATE DATABASE IF NOT EXISTS ollama_chat CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE ollama_chat;

-- 用户表
CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '用户ID',
    username VARCHAR(50) UNIQUE NOT NULL COMMENT '用户名',
    password VARCHAR(255) NOT NULL COMMENT '密码（BCrypt加密）',
    email VARCHAR(100) UNIQUE NOT NULL COMMENT '邮箱',
    phone VARCHAR(20) COMMENT '手机号',
    avatar_url VARCHAR(255) COMMENT '头像URL',
    is_active BOOLEAN DEFAULT TRUE COMMENT '是否激活',
    created_at DATETIME COMMENT '创建时间',
    updated_at DATETIME COMMENT '更新时间',
    INDEX idx_username (username),
    INDEX idx_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 对话表
CREATE TABLE IF NOT EXISTS conversation (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '对话ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    session_id VARCHAR(64) UNIQUE NOT NULL COMMENT '会话ID',
    title VARCHAR(200) COMMENT '对话标题',
    model_name VARCHAR(50) COMMENT '模型名称',
    is_active BOOLEAN DEFAULT TRUE COMMENT '是否活跃',
    created_at DATETIME COMMENT '创建时间',
    updated_at DATETIME COMMENT '更新时间',
    INDEX idx_user_id (user_id),
    INDEX idx_session_id (session_id),
    FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='对话表';

-- 消息表
CREATE TABLE IF NOT EXISTS message (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '消息ID',
    conversation_id BIGINT NOT NULL COMMENT '对话ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    role VARCHAR(20) NOT NULL COMMENT '角色（system/user/assistant）',
    content TEXT NOT NULL COMMENT '消息内容',
    image_url LONGTEXT COMMENT '图片URL',
    message_order INT COMMENT '消息顺序',
    created_at DATETIME COMMENT '创建时间',
    INDEX idx_conversation_id (conversation_id),
    INDEX idx_user_id (user_id),
    INDEX idx_message_order (message_order),
    FOREIGN KEY (conversation_id) REFERENCES conversation(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息表';

-- 知识库文档表
CREATE TABLE IF NOT EXISTS knowledge_document (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    title VARCHAR(255) NOT NULL COMMENT '文档标题',
    status VARCHAR(60) NOT NULL COMMENT '状态',
    segment_count INT NOT NULL DEFAULT 0 COMMENT '分片数量',
    summary LONGTEXT COMMENT '文档摘要',
    created_at DATETIME NOT NULL COMMENT '创建时间',
    updated_at DATETIME NOT NULL COMMENT '更新时间',
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库文档表';

-- 知识库分片表
CREATE TABLE IF NOT EXISTS knowledge_segment (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    document_id BIGINT NOT NULL COMMENT '文档ID',
    segment_index INT NOT NULL COMMENT '分片索引',
    content TEXT NOT NULL COMMENT '分片内容',
    chroma_id VARCHAR(80) NOT NULL COMMENT '向量库ID',
    created_at DATETIME NOT NULL COMMENT '创建时间',
    INDEX idx_document_id (document_id),
    FOREIGN KEY (document_id) REFERENCES knowledge_document(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库分片表';

-- 插入测试用户（密码：password123）
INSERT INTO sys_user (username, password, email, phone, is_active, created_at, updated_at)
VALUES ('testuser', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', 'test@example.com', '13800138000', TRUE, NOW(), NOW())
ON DUPLICATE KEY UPDATE username=username;
