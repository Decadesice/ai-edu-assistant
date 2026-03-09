CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(50) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    email VARCHAR(100) UNIQUE NOT NULL,
    phone VARCHAR(20),
    avatar_url VARCHAR(255),
    is_active BOOLEAN DEFAULT TRUE,
    created_at DATETIME,
    updated_at DATETIME,
    INDEX idx_sys_user_username (username),
    INDEX idx_sys_user_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS conversation (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    session_id VARCHAR(64) UNIQUE NOT NULL,
    title VARCHAR(200),
    model_name VARCHAR(50),
    is_active BOOLEAN DEFAULT TRUE,
    created_at DATETIME,
    updated_at DATETIME,
    INDEX idx_conversation_user_id (user_id),
    INDEX idx_conversation_session_id (session_id),
    CONSTRAINT fk_conversation_user FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS message (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    conversation_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    role VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    image_url LONGTEXT,
    message_order INT,
    created_at DATETIME,
    INDEX idx_message_conversation_id (conversation_id),
    INDEX idx_message_user_id (user_id),
    INDEX idx_message_message_order (message_order),
    CONSTRAINT fk_message_conversation FOREIGN KEY (conversation_id) REFERENCES conversation(id) ON DELETE CASCADE,
    CONSTRAINT fk_message_user FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS knowledge_document (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    status VARCHAR(60) NOT NULL,
    segment_count INT NOT NULL DEFAULT 0,
    summary LONGTEXT,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    INDEX idx_knowledge_document_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS knowledge_segment (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    document_id BIGINT NOT NULL,
    segment_index INT NOT NULL,
    content TEXT NOT NULL,
    chroma_id VARCHAR(80) NOT NULL,
    created_at DATETIME NOT NULL,
    INDEX idx_knowledge_segment_document_id (document_id),
    CONSTRAINT fk_knowledge_segment_document FOREIGN KEY (document_id) REFERENCES knowledge_document(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ingest_task (
    id VARCHAR(36) PRIMARY KEY,
    user_id BIGINT NOT NULL,
    document_id BIGINT NOT NULL,
    status VARCHAR(40) NOT NULL,
    progress INT NOT NULL,
    processed_segments INT NOT NULL,
    total_segments INT NOT NULL,
    file_path VARCHAR(1024) NOT NULL,
    error_message LONGTEXT,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    INDEX idx_ingest_task_user_id (user_id),
    INDEX idx_ingest_task_document_id (document_id),
    CONSTRAINT fk_ingest_task_document FOREIGN KEY (document_id) REFERENCES knowledge_document(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS generated_question (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    document_id BIGINT NOT NULL,
    topic VARCHAR(120) NOT NULL,
    question_type VARCHAR(20) NOT NULL,
    stem VARCHAR(2000) NOT NULL,
    options_json TEXT,
    answer VARCHAR(255) NOT NULL,
    explanation VARCHAR(2000) NOT NULL,
    created_at DATETIME NOT NULL,
    INDEX idx_generated_question_user_id (user_id),
    INDEX idx_generated_question_document_id (document_id),
    CONSTRAINT fk_generated_question_document FOREIGN KEY (document_id) REFERENCES knowledge_document(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS question_attempt (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    question_id BIGINT NOT NULL,
    chosen VARCHAR(10) NOT NULL,
    correct BOOLEAN NOT NULL,
    created_at DATETIME NOT NULL,
    INDEX idx_question_attempt_user_id (user_id),
    INDEX idx_question_attempt_question_id (question_id),
    CONSTRAINT fk_question_attempt_question FOREIGN KEY (question_id) REFERENCES generated_question(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS wrong_question_group (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    name VARCHAR(60) NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    INDEX idx_wrong_question_group_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
