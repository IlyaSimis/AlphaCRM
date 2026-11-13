--liquibase formatted sql

--changeset quietclinic:019
CREATE TABLE telephony_call (
  id UUID PRIMARY KEY,
  clinic_id UUID NOT NULL REFERENCES clinic(id),
  patient_id UUID REFERENCES patient(id),
  entry_id VARCHAR(255),
  call_id VARCHAR(255),
  command_id VARCHAR(255),
  direction VARCHAR(16) NOT NULL,
  phone VARCHAR(64) NOT NULL,
  extension VARCHAR(32),
  status VARCHAR(40) NOT NULL,
  sequence_number INTEGER NOT NULL DEFAULT 0,
  recording_id VARCHAR(255),
  occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
  created_by UUID
);
CREATE UNIQUE INDEX telephony_call_call_idx ON telephony_call(clinic_id, call_id);
CREATE UNIQUE INDEX telephony_call_command_idx ON telephony_call(clinic_id, command_id);
CREATE INDEX telephony_call_patient_idx ON telephony_call(patient_id, occurred_at DESC);

CREATE TABLE telephony_event (
  id UUID PRIMARY KEY,
  clinic_id UUID NOT NULL REFERENCES clinic(id),
  entry_id VARCHAR(255) NOT NULL,
  call_id VARCHAR(255) NOT NULL,
  sequence_number INTEGER NOT NULL,
  event_type VARCHAR(40) NOT NULL,
  received_at TIMESTAMP WITH TIME ZONE NOT NULL,
  CONSTRAINT telephony_event_unique UNIQUE(clinic_id, entry_id, call_id, sequence_number, event_type)
);
