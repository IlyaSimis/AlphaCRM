--liquibase formatted sql

--changeset quietclinic:058
-- Персональные настройки относятся только к внутреннему чату сотрудника.
CREATE TABLE staff_chat_preferences (
  staff_id UUID PRIMARY KEY REFERENCES staff_user(id) ON DELETE CASCADE,
  notifications_enabled BOOLEAN NOT NULL DEFAULT TRUE,
  sound_enabled BOOLEAN NOT NULL DEFAULT TRUE,
  browser_enabled BOOLEAN NOT NULL DEFAULT FALSE,
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- Упоминания хранят идентификаторы, а не полагаются на изменяемые имена сотрудников.
CREATE TABLE staff_chat_message_mention (
  message_id BIGINT NOT NULL REFERENCES staff_chat_message(id) ON DELETE CASCADE,
  staff_id UUID NOT NULL REFERENCES staff_user(id) ON DELETE CASCADE,
  PRIMARY KEY (message_id, staff_id)
);
CREATE INDEX staff_chat_message_mention_staff_idx
  ON staff_chat_message_mention(staff_id, message_id);
