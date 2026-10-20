--liquibase formatted sql

--changeset quietclinic:001
CREATE TABLE patient (
  id UUID PRIMARY KEY,
  full_name VARCHAR(255) NOT NULL,
  birth_date DATE NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE TABLE patient_contact (
  id UUID PRIMARY KEY,
  patient_id UUID NOT NULL REFERENCES patient(id),
  type VARCHAR(40) NOT NULL,
  value VARCHAR(255) NOT NULL
);
CREATE TABLE patient_consent (
  id UUID PRIMARY KEY,
  patient_id UUID NOT NULL REFERENCES patient(id),
  version VARCHAR(80) NOT NULL,
  basis VARCHAR(120) NOT NULL,
  granted_at TIMESTAMP WITH TIME ZONE NOT NULL,
  revoked_at TIMESTAMP WITH TIME ZONE
);
CREATE TABLE practitioner (
  id UUID PRIMARY KEY,
  full_name VARCHAR(255) NOT NULL,
  specialty VARCHAR(120) NOT NULL
);
CREATE TABLE practitioner_schedule (
  id UUID PRIMARY KEY,
  practitioner_id UUID NOT NULL REFERENCES practitioner(id),
  day_of_week SMALLINT NOT NULL,
  starts_at TIME NOT NULL,
  ends_at TIME NOT NULL
);
CREATE TABLE appointment (
  id UUID PRIMARY KEY,
  patient_id UUID NOT NULL REFERENCES patient(id),
  practitioner_id UUID NOT NULL REFERENCES practitioner(id),
  starts_at TIMESTAMP WITH TIME ZONE NOT NULL,
  ends_at TIMESTAMP WITH TIME ZONE NOT NULL,
  service_name VARCHAR(255) NOT NULL,
  status VARCHAR(40) NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT appointment_interval_valid CHECK (ends_at > starts_at)
);
CREATE INDEX appointment_practitioner_time_idx ON appointment(practitioner_id, starts_at, ends_at);
CREATE TABLE care_relationship (
  id UUID PRIMARY KEY,
  practitioner_id UUID NOT NULL REFERENCES practitioner(id),
  patient_id UUID NOT NULL REFERENCES patient(id),
  valid_from TIMESTAMP WITH TIME ZONE NOT NULL,
  valid_until TIMESTAMP WITH TIME ZONE NOT NULL,
  reason VARCHAR(255) NOT NULL
);
CREATE TABLE encounter (
  id UUID PRIMARY KEY,
  appointment_id UUID NOT NULL REFERENCES appointment(id),
  patient_id UUID NOT NULL REFERENCES patient(id),
  practitioner_id UUID NOT NULL REFERENCES practitioner(id),
  status VARCHAR(40) NOT NULL,
  started_at TIMESTAMP WITH TIME ZONE NOT NULL,
  completed_at TIMESTAMP WITH TIME ZONE
);
CREATE TABLE clinical_note (
  id UUID PRIMARY KEY,
  encounter_id UUID NOT NULL UNIQUE REFERENCES encounter(id),
  complaints_encrypted VARCHAR(16000) NOT NULL,
  mental_status_encrypted VARCHAR(16000) NOT NULL,
  diagnosis_encrypted VARCHAR(16000) NOT NULL,
  recommendations_encrypted VARCHAR(16000) NOT NULL,
  author_id UUID NOT NULL,
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
  version BIGINT NOT NULL DEFAULT 0
);
CREATE TABLE medical_document (
  id UUID PRIMARY KEY,
  encounter_id UUID NOT NULL REFERENCES encounter(id),
  amends_document_id UUID REFERENCES medical_document(id),
  state VARCHAR(40) NOT NULL,
  template_version VARCHAR(80) NOT NULL,
  snapshot_encrypted VARCHAR(32000) NOT NULL,
  content_hash VARCHAR(64),
  signed_by UUID,
  signed_at TIMESTAMP WITH TIME ZONE,
  version BIGINT NOT NULL DEFAULT 0
);
CREATE TABLE charge (
  id UUID PRIMARY KEY,
  appointment_id UUID NOT NULL REFERENCES appointment(id),
  amount_minor BIGINT NOT NULL,
  currency VARCHAR(3) NOT NULL,
  status VARCHAR(40) NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE TABLE payment_record (
  id UUID PRIMARY KEY,
  idempotency_key VARCHAR(255) NOT NULL UNIQUE,
  charge_id UUID NOT NULL REFERENCES charge(id),
  provider_ref VARCHAR(255) NOT NULL,
  payment_status VARCHAR(40) NOT NULL,
  fiscal_status VARCHAR(40) NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE TABLE outbox_message (
  id UUID PRIMARY KEY,
  topic VARCHAR(80) NOT NULL,
  aggregate_type VARCHAR(80) NOT NULL,
  aggregate_id UUID NOT NULL,
  payload VARCHAR(4000) NOT NULL,
  status VARCHAR(40) NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE TABLE audit_event (
  id UUID PRIMARY KEY,
  occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
  actor_id UUID NOT NULL,
  action VARCHAR(120) NOT NULL,
  object_type VARCHAR(80) NOT NULL,
  object_id UUID NOT NULL,
  result VARCHAR(40) NOT NULL,
  reason VARCHAR(255),
  correlation_id VARCHAR(80)
);
CREATE INDEX audit_occurred_idx ON audit_event(occurred_at DESC);

INSERT INTO patient(id, full_name, birth_date, created_at) VALUES
('00000000-0000-0000-0000-000000000101', 'Анна Михайлова', DATE '1991-05-14', CURRENT_TIMESTAMP),
('00000000-0000-0000-0000-000000000102', 'Дмитрий Волков', DATE '1985-11-02', CURRENT_TIMESTAMP),
('00000000-0000-0000-0000-000000000103', 'Мария Соколова', DATE '1996-02-28', CURRENT_TIMESTAMP);
INSERT INTO patient_contact(id, patient_id, type, value) VALUES
('00000000-0000-0000-0000-000000000111', '00000000-0000-0000-0000-000000000101', 'PHONE', '+79162451872'),
('00000000-0000-0000-0000-000000000112', '00000000-0000-0000-0000-000000000102', 'PHONE', '+79037824416'),
('00000000-0000-0000-0000-000000000113', '00000000-0000-0000-0000-000000000103', 'PHONE', '+79256630241');
INSERT INTO patient_consent(id, patient_id, version, basis, granted_at) VALUES
('00000000-0000-0000-0000-000000000121', '00000000-0000-0000-0000-000000000101', 'v1.2', 'SIGNED_FORM', CURRENT_TIMESTAMP),
('00000000-0000-0000-0000-000000000122', '00000000-0000-0000-0000-000000000102', 'v1.2', 'SIGNED_FORM', CURRENT_TIMESTAMP);
INSERT INTO practitioner(id, full_name, specialty) VALUES
('00000000-0000-0000-0000-000000000201', 'Александр Петров', 'Психиатр');
INSERT INTO practitioner_schedule(id, practitioner_id, day_of_week, starts_at, ends_at) VALUES
('00000000-0000-0000-0000-000000000211', '00000000-0000-0000-0000-000000000201', 2, TIME '09:00:00', TIME '18:00:00');
INSERT INTO appointment(id, patient_id, practitioner_id, starts_at, ends_at, service_name, status, created_at, version) VALUES
('00000000-0000-0000-0000-000000000301', '00000000-0000-0000-0000-000000000101', '00000000-0000-0000-0000-000000000201', TIMESTAMP WITH TIME ZONE '2026-10-20 09:00:00+00', TIMESTAMP WITH TIME ZONE '2026-10-20 09:50:00+00', 'Первичный приём', 'ARRIVED', CURRENT_TIMESTAMP, 0),
('00000000-0000-0000-0000-000000000302', '00000000-0000-0000-0000-000000000102', '00000000-0000-0000-0000-000000000201', TIMESTAMP WITH TIME ZONE '2026-10-20 10:10:00+00', TIMESTAMP WITH TIME ZONE '2026-10-20 10:40:00+00', 'Повторный приём', 'CONFIRMED', CURRENT_TIMESTAMP, 0);
INSERT INTO care_relationship(id, practitioner_id, patient_id, valid_from, valid_until, reason) VALUES
('00000000-0000-0000-0000-000000000401', '00000000-0000-0000-0000-000000000201', '00000000-0000-0000-0000-000000000101', TIMESTAMP WITH TIME ZONE '2026-01-01 00:00:00+00', TIMESTAMP WITH TIME ZONE '2027-01-01 00:00:00+00', 'Назначенный визит');
