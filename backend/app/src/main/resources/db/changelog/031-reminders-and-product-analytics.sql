--liquibase formatted sql

--changeset quietclinic:053 dbms:postgresql
CREATE TABLE clinic_notification_settings (
  clinic_id UUID PRIMARY KEY REFERENCES clinic(id) ON DELETE CASCADE,
  reminder_24h_enabled BOOLEAN NOT NULL DEFAULT TRUE,
  reminder_3h_enabled BOOLEAN NOT NULL DEFAULT FALSE,
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

ALTER TABLE integration DROP CONSTRAINT integration_type_check;
ALTER TABLE integration ADD CONSTRAINT integration_type_check
  CHECK (type IN ('TELEGRAM', 'MAX', 'MANGO', 'WEBHOOK', 'CASHBOX', 'YANDEX_METRICA'));

--changeset quietclinic:054 dbms:h2
CREATE TABLE clinic_notification_settings (
  clinic_id UUID PRIMARY KEY REFERENCES clinic(id) ON DELETE CASCADE,
  reminder_24h_enabled BOOLEAN NOT NULL DEFAULT TRUE,
  reminder_3h_enabled BOOLEAN NOT NULL DEFAULT FALSE,
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

ALTER TABLE integration DROP CONSTRAINT integration_type_check;
ALTER TABLE integration ADD CONSTRAINT integration_type_check
  CHECK (type IN ('TELEGRAM', 'MAX', 'MANGO', 'WEBHOOK', 'CASHBOX', 'YANDEX_METRICA'));
