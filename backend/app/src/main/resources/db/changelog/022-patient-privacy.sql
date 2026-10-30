--liquibase formatted sql

--changeset quietclinic:092 dbms:postgresql
ALTER TABLE patient ALTER COLUMN profile TYPE TEXT USING profile::text;
ALTER TABLE patient_contact ALTER COLUMN contact_value TYPE VARCHAR(2048);
CREATE TABLE patient_contact_token (
  contact_id UUID NOT NULL REFERENCES patient_contact(id) ON DELETE CASCADE,
  patient_id UUID NOT NULL REFERENCES patient(id) ON DELETE CASCADE,
  clinic_id UUID NOT NULL,
  kind VARCHAR(40) NOT NULL,
  token CHAR(64) NOT NULL,
  PRIMARY KEY(contact_id, token)
);
CREATE INDEX patient_contact_token_lookup_idx ON patient_contact_token(clinic_id, kind, token);

--changeset quietclinic:093 dbms:h2
ALTER TABLE patient ALTER COLUMN profile VARCHAR(1000000);
ALTER TABLE patient_contact ALTER COLUMN contact_value VARCHAR(2048);
CREATE TABLE patient_contact_token (
  contact_id UUID NOT NULL REFERENCES patient_contact(id) ON DELETE CASCADE,
  patient_id UUID NOT NULL REFERENCES patient(id) ON DELETE CASCADE,
  clinic_id UUID NOT NULL,
  kind VARCHAR(40) NOT NULL,
  token CHAR(64) NOT NULL,
  PRIMARY KEY(contact_id, token)
);
CREATE INDEX patient_contact_token_lookup_idx ON patient_contact_token(clinic_id, kind, token);
