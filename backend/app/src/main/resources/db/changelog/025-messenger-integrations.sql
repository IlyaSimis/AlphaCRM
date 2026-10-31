--liquibase formatted sql

--changeset quietclinic:096
ALTER TABLE patient ADD COLUMN preferred_messenger VARCHAR(16);
CREATE TABLE messenger_link_token (
  id UUID NOT NULL PRIMARY KEY,
  patient_id UUID NOT NULL REFERENCES patient(id) ON DELETE CASCADE,
  clinic_id UUID NOT NULL,
  channel VARCHAR(16) NOT NULL,
  token_hash CHAR(64) NOT NULL UNIQUE,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
  consumed_at TIMESTAMP WITH TIME ZONE
);
CREATE INDEX messenger_link_token_patient_idx ON messenger_link_token(patient_id, channel);

--changeset quietclinic:097
ALTER TABLE patient ADD CONSTRAINT patient_preferred_messenger_ck
  CHECK (preferred_messenger IS NULL OR preferred_messenger IN ('TELEGRAM', 'MAX'));
ALTER TABLE messenger_link_token ADD CONSTRAINT messenger_link_channel_ck
  CHECK (channel IN ('TELEGRAM', 'MAX'));
