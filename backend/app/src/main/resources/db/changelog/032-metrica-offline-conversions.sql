--liquibase formatted sql

--changeset quietclinic:055 dbms:postgresql
ALTER TABLE appointment ADD COLUMN metrica_client_id VARCHAR(64);

CREATE TABLE metrica_offline_conversion (
  appointment_id UUID PRIMARY KEY REFERENCES appointment(id) ON DELETE CASCADE,
  clinic_id UUID NOT NULL REFERENCES clinic(id) ON DELETE CASCADE,
  target VARCHAR(20) NOT NULL,
  status VARCHAR(16) NOT NULL,
  conversion_at TIMESTAMP WITH TIME ZONE NOT NULL,
  price_minor BIGINT,
  currency VARCHAR(3),
  uploading_id VARCHAR(80),
  attempts INTEGER NOT NULL DEFAULT 0,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
  uploaded_at TIMESTAMP WITH TIME ZONE,
  last_error VARCHAR(120),
  CONSTRAINT metrica_offline_conversion_status_check
    CHECK (status IN ('PENDING', 'UPLOADING', 'UPLOADED', 'FAILED'))
);

CREATE INDEX metrica_offline_conversion_clinic_status_idx
  ON metrica_offline_conversion(clinic_id, status, created_at);

--changeset quietclinic:056 dbms:h2
ALTER TABLE appointment ADD COLUMN metrica_client_id VARCHAR(64);

CREATE TABLE metrica_offline_conversion (
  appointment_id UUID PRIMARY KEY REFERENCES appointment(id) ON DELETE CASCADE,
  clinic_id UUID NOT NULL REFERENCES clinic(id) ON DELETE CASCADE,
  target VARCHAR(20) NOT NULL,
  status VARCHAR(16) NOT NULL,
  conversion_at TIMESTAMP WITH TIME ZONE NOT NULL,
  price_minor BIGINT,
  currency VARCHAR(3),
  uploading_id VARCHAR(80),
  attempts INTEGER NOT NULL DEFAULT 0,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
  uploaded_at TIMESTAMP WITH TIME ZONE,
  last_error VARCHAR(120),
  CONSTRAINT metrica_offline_conversion_status_check
    CHECK (status IN ('PENDING', 'UPLOADING', 'UPLOADED', 'FAILED'))
);

CREATE INDEX metrica_offline_conversion_clinic_status_idx
  ON metrica_offline_conversion(clinic_id, status, created_at);
