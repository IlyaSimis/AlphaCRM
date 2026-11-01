--liquibase formatted sql

--changeset quietclinic:051 dbms:postgresql
CREATE TABLE integration (
  id UUID PRIMARY KEY,
  clinic_id UUID NOT NULL REFERENCES clinic(id) ON DELETE CASCADE,
  type VARCHAR(32) NOT NULL,
  status VARCHAR(16) NOT NULL,
  public_key UUID NOT NULL UNIQUE,
  settings VARCHAR(4000) NOT NULL,
  active_secret_version INTEGER,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
  CONSTRAINT integration_clinic_type_unique UNIQUE (clinic_id, type),
  CONSTRAINT integration_type_check CHECK (type IN ('TELEGRAM', 'MAX', 'MANGO', 'WEBHOOK', 'CASHBOX')),
  CONSTRAINT integration_status_check CHECK (status IN ('PENDING', 'ACTIVE', 'DISABLED', 'ERROR'))
);

CREATE TABLE integration_secret (
  integration_id UUID NOT NULL REFERENCES integration(id) ON DELETE CASCADE,
  version INTEGER NOT NULL,
  ciphertext BYTEA NOT NULL,
  wrapped_data_key BYTEA NOT NULL,
  key_version VARCHAR(80) NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  PRIMARY KEY (integration_id, version)
);

--changeset quietclinic:052 dbms:h2
CREATE TABLE integration (
  id UUID PRIMARY KEY,
  clinic_id UUID NOT NULL REFERENCES clinic(id) ON DELETE CASCADE,
  type VARCHAR(32) NOT NULL,
  status VARCHAR(16) NOT NULL,
  public_key UUID NOT NULL UNIQUE,
  settings VARCHAR(4000) NOT NULL,
  active_secret_version INTEGER,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
  CONSTRAINT integration_clinic_type_unique UNIQUE (clinic_id, type),
  CONSTRAINT integration_type_check CHECK (type IN ('TELEGRAM', 'MAX', 'MANGO', 'WEBHOOK', 'CASHBOX')),
  CONSTRAINT integration_status_check CHECK (status IN ('PENDING', 'ACTIVE', 'DISABLED', 'ERROR'))
);

CREATE TABLE integration_secret (
  integration_id UUID NOT NULL REFERENCES integration(id) ON DELETE CASCADE,
  version INTEGER NOT NULL,
  ciphertext LONGVARBINARY NOT NULL,
  wrapped_data_key LONGVARBINARY NOT NULL,
  key_version VARCHAR(80) NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  PRIMARY KEY (integration_id, version)
);
