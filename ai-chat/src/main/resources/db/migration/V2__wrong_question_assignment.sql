CREATE TABLE IF NOT EXISTS wrong_question_assignment (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    question_id BIGINT NOT NULL,
    group_id BIGINT NULL,
    updated_at DATETIME NOT NULL,
    UNIQUE KEY uk_wrong_question_assignment_user_question (user_id, question_id),
    INDEX idx_wrong_question_assignment_user_id (user_id),
    INDEX idx_wrong_question_assignment_group_id (group_id),
    CONSTRAINT fk_wrong_question_assignment_group FOREIGN KEY (group_id) REFERENCES wrong_question_group(id) ON DELETE SET NULL,
    CONSTRAINT fk_wrong_question_assignment_question FOREIGN KEY (question_id) REFERENCES generated_question(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
