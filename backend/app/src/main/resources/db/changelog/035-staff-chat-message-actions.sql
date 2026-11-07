--liquibase formatted sql

--changeset quietclinic:059
ALTER TABLE staff_chat_message ADD COLUMN edited_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE staff_chat_message ADD COLUMN deleted_at TIMESTAMP WITH TIME ZONE;

CREATE TABLE staff_chat_reaction (
  message_id BIGINT NOT NULL REFERENCES staff_chat_message(id) ON DELETE CASCADE,
  staff_id UUID NOT NULL REFERENCES staff_user(id) ON DELETE CASCADE,
  emoji VARCHAR(16) NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  PRIMARY KEY (message_id, staff_id)
);
CREATE INDEX staff_chat_reaction_staff_idx ON staff_chat_reaction(staff_id, message_id);

-- Содержимое лежит в clinic.chat.storage-dir, в БД остаются только метаданные.
CREATE TABLE staff_chat_attachment (
  id UUID PRIMARY KEY,
  message_id BIGINT NOT NULL REFERENCES staff_chat_message(id) ON DELETE CASCADE,
  original_name VARCHAR(255) NOT NULL,
  content_type VARCHAR(255) NOT NULL,
  size_bytes BIGINT NOT NULL,
  storage_key VARCHAR(73) NOT NULL UNIQUE
);
CREATE INDEX staff_chat_attachment_message_idx ON staff_chat_attachment(message_id, id);
