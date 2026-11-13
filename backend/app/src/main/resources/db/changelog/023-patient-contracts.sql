--liquibase formatted sql

--changeset quietclinic:094
CREATE TABLE contract_template (
  id UUID PRIMARY KEY,
  clinic_id UUID NOT NULL REFERENCES clinic(id),
  name VARCHAR(200) NOT NULL,
  original_name VARCHAR(255) NOT NULL,
  version INTEGER NOT NULL,
  placeholders VARCHAR(4000) NOT NULL,
  docx_ciphertext BYTEA NOT NULL,
  created_by UUID NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX contract_template_clinic_idx ON contract_template(clinic_id, name, version);

CREATE TABLE contract_counter (
  clinic_id UUID PRIMARY KEY REFERENCES clinic(id),
  last_number BIGINT NOT NULL
);

CREATE TABLE patient_contract (
  id UUID PRIMARY KEY,
  clinic_id UUID NOT NULL REFERENCES clinic(id),
  patient_id UUID NOT NULL REFERENCES patient(id),
  template_id UUID NOT NULL REFERENCES contract_template(id),
  service_id UUID REFERENCES clinic_service(id),
  contract_number VARCHAR(80) NOT NULL,
  service_name VARCHAR(255),
  snapshot_values TEXT NOT NULL,
  docx_ciphertext BYTEA NOT NULL,
  pdf_ciphertext BYTEA,
  docx_hash CHAR(64) NOT NULL,
  pdf_hash CHAR(64),
  created_by UUID NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  CONSTRAINT patient_contract_number_unique UNIQUE(clinic_id, contract_number)
);
CREATE INDEX patient_contract_patient_idx ON patient_contract(clinic_id, patient_id, created_at);
