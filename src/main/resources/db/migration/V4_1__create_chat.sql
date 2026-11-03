CREATE TABLE chat_conversations (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  customer_id BIGINT NOT NULL,
  employee_id BIGINT NOT NULL,
  customer_read_id BIGINT NOT NULL DEFAULT 0,
  employee_read_id BIGINT NOT NULL DEFAULT 0,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uq_chat_pair UNIQUE (customer_id, employee_id),
  CONSTRAINT fk_chat_customer FOREIGN KEY (customer_id) REFERENCES app_users(id),
  CONSTRAINT fk_chat_employee FOREIGN KEY (employee_id) REFERENCES app_users(id)
);
CREATE INDEX idx_chat_employee ON chat_conversations(employee_id);
CREATE TABLE chat_messages (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  conversation_id BIGINT NOT NULL,
  sender_id BIGINT NOT NULL,
  client_id VARCHAR(36) NOT NULL,
  content VARCHAR(2000) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_message_conversation FOREIGN KEY (conversation_id) REFERENCES chat_conversations(id),
  CONSTRAINT fk_message_sender FOREIGN KEY (sender_id) REFERENCES app_users(id),
  CONSTRAINT uq_chat_retry UNIQUE (conversation_id, sender_id, client_id)
);
CREATE INDEX idx_chat_history ON chat_messages(conversation_id, id);
