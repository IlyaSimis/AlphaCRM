--liquibase formatted sql

--changeset quietclinic:036
-- Экстренный доступ владельца (решение K1): один пациент, только чтение, 60 минут. Причина — код из
-- списка и текст; текст шифруется и хранится только здесь, в журнал аудита не попадает.
CREATE TABLE break_glass_grant (
  id UUID PRIMARY KEY,
  clinic_id UUID NOT NULL REFERENCES clinic(id),
  patient_id UUID NOT NULL REFERENCES patient(id),
  owner_id UUID NOT NULL,
  reason_code VARCHAR(40) NOT NULL,
  reason_text_encrypted VARCHAR(4000) NOT NULL,
  granted_at TIMESTAMP WITH TIME ZONE NOT NULL,
  expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
  CONSTRAINT break_glass_grant_reason_valid CHECK (
    reason_code IN ('PATIENT_COMPLAINT', 'LEGAL_REQUEST', 'DOCTOR_UNAVAILABLE', 'QUALITY_REVIEW', 'OTHER')),
  CONSTRAINT break_glass_grant_period_valid CHECK (expires_at > granted_at)
);
CREATE INDEX break_glass_grant_owner_idx ON break_glass_grant(owner_id, patient_id, expires_at);
CREATE INDEX break_glass_grant_clinic_idx ON break_glass_grant(clinic_id, granted_at);
