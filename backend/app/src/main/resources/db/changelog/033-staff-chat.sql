--liquibase formatted sql

--changeset quietclinic:057
-- Внутренний чат сотрудников. Пациенты и внешние боты не имеют записей staff_user и не могут
-- стать участниками. Сообщения не входят в клиническую историю пациента.
CREATE TABLE staff_conversation (
  id UUID PRIMARY KEY,
  clinic_id UUID NOT NULL REFERENCES clinic(id) ON DELETE CASCADE,
  kind VARCHAR(10) NOT NULL,
  direct_key VARCHAR(73),
  title VARCHAR(100),
  created_by UUID NOT NULL REFERENCES staff_user(id),
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
  CONSTRAINT staff_conversation_kind_check CHECK (kind IN ('DIRECT', 'GROUP')),
  CONSTRAINT staff_conversation_direct_unique UNIQUE (clinic_id, direct_key)
);

CREATE TABLE staff_conversation_member (
  conversation_id UUID NOT NULL REFERENCES staff_conversation(id) ON DELETE CASCADE,
  staff_id UUID NOT NULL REFERENCES staff_user(id) ON DELETE CASCADE,
  joined_at TIMESTAMP WITH TIME ZONE NOT NULL,
  PRIMARY KEY (conversation_id, staff_id)
);
CREATE INDEX staff_conversation_member_staff_idx
  ON staff_conversation_member(staff_id, conversation_id);

CREATE SEQUENCE staff_chat_message_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE staff_chat_message (
  id BIGINT PRIMARY KEY,
  conversation_id UUID NOT NULL REFERENCES staff_conversation(id) ON DELETE CASCADE,
  clinic_id UUID NOT NULL REFERENCES clinic(id) ON DELETE CASCADE,
  sender_id UUID NOT NULL REFERENCES staff_user(id),
  body VARCHAR(4000) NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX staff_chat_message_conversation_idx
  ON staff_chat_message(conversation_id, id);

CREATE TABLE staff_chat_read_state (
  conversation_id UUID NOT NULL REFERENCES staff_conversation(id) ON DELETE CASCADE,
  staff_id UUID NOT NULL REFERENCES staff_user(id) ON DELETE CASCADE,
  last_read_message_id BIGINT REFERENCES staff_chat_message(id),
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
  PRIMARY KEY (conversation_id, staff_id)
);
