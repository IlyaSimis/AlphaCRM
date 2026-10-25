--liquibase formatted sql

--changeset quietclinic:023
-- Доставка outbox: та же очередь outbox_message, без второй таблицы. Строка хранит только
-- машинные поля: шаблон, ключ идемпотентности, время записи, канал и итог попытки. Текст
-- сообщения, телефон и chat id в строку не попадают — текст собирается из записи при доставке.
ALTER TABLE outbox_message ADD COLUMN template VARCHAR(60);
ALTER TABLE outbox_message ADD COLUMN clinic_id UUID;
ALTER TABLE outbox_message ADD COLUMN idempotency_key VARCHAR(160);
ALTER TABLE outbox_message ADD COLUMN appointment_starts_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE outbox_message ADD COLUMN next_attempt_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE outbox_message ADD COLUMN last_attempt_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE outbox_message ADD COLUMN sent_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE outbox_message ADD COLUMN attempts INTEGER DEFAULT 0 NOT NULL;
ALTER TABLE outbox_message ADD COLUMN channel VARCHAR(40);
ALTER TABLE outbox_message ADD COLUMN last_error VARCHAR(80);
ALTER TABLE outbox_message ADD COLUMN version BIGINT DEFAULT 0 NOT NULL;

-- Прежние строки: шаблон из payload, клиника и время — из записи или платежа.
UPDATE outbox_message SET template = 'appointment-confirmed'
  WHERE topic = 'NOTIFICATION' AND payload LIKE '%appointment-confirmed%';
UPDATE outbox_message SET template = 'appointment-cancelled'
  WHERE topic = 'NOTIFICATION' AND payload LIKE '%appointment-cancelled%';
UPDATE outbox_message SET template = 'receipt' WHERE topic = 'FISCALIZATION';
UPDATE outbox_message SET template = 'unknown' WHERE template IS NULL;
UPDATE outbox_message SET
  appointment_starts_at = (SELECT a.starts_at FROM appointment a WHERE a.id = outbox_message.aggregate_id),
  clinic_id = (SELECT p.clinic_id FROM appointment a JOIN practitioner p ON p.id = a.practitioner_id
               WHERE a.id = outbox_message.aggregate_id)
  WHERE aggregate_type = 'APPOINTMENT';
UPDATE outbox_message SET clinic_id = (
  SELECT p.clinic_id FROM payment_record r
    JOIN charge c ON c.id = r.charge_id
    JOIN appointment a ON a.id = c.appointment_id
    JOIN practitioner p ON p.id = a.practitioner_id
  WHERE r.id = outbox_message.aggregate_id)
  WHERE aggregate_type = 'PAYMENT';
UPDATE outbox_message SET clinic_id = '00000000-0000-0000-0000-00000000c001' WHERE clinic_id IS NULL;
UPDATE outbox_message SET idempotency_key = 'legacy:' || CAST(id AS VARCHAR(36));
UPDATE outbox_message SET next_attempt_at = created_at WHERE status = 'PENDING';
-- Новых значений полезной нагрузки у старых строк нет: текст всё равно собирается из записи.
UPDATE outbox_message SET payload = '{"template":"' || template || '"}';

ALTER TABLE outbox_message ALTER COLUMN template SET NOT NULL;
ALTER TABLE outbox_message ALTER COLUMN clinic_id SET NOT NULL;
ALTER TABLE outbox_message ALTER COLUMN idempotency_key SET NOT NULL;
ALTER TABLE outbox_message ADD CONSTRAINT outbox_message_clinic_fk FOREIGN KEY (clinic_id) REFERENCES clinic(id);
ALTER TABLE outbox_message ADD CONSTRAINT outbox_message_idempotency_unique UNIQUE (idempotency_key);
ALTER TABLE outbox_message ADD CONSTRAINT outbox_message_status_valid CHECK (
  status IN ('PENDING', 'SENDING', 'SENT', 'FAILED', 'CANCELLED', 'SKIPPED'));
CREATE INDEX outbox_message_due_idx ON outbox_message(status, next_attempt_at);
CREATE INDEX outbox_message_clinic_idx ON outbox_message(clinic_id, created_at);
CREATE INDEX outbox_message_aggregate_idx ON outbox_message(aggregate_id);

--changeset quietclinic:024
-- Контакт Telegram пациента: chat id, который пациент получает, начав диалог с ботом клиники.
-- Хранится как остальные контакты; один на пациента.
CREATE INDEX patient_contact_patient_type_idx ON patient_contact(patient_id, type);
