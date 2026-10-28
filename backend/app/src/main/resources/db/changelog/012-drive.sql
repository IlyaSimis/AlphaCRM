--liquibase formatted sql

--changeset quietclinic:034
-- Корпоративный диск (раздел H): папки и файлы, версии, права и ссылки, квота клиники. Схема
-- drive — только для модуля drive. Байты файлов — в хранилище объектов (ключ clinicId/uuid,
-- шифртекст); имя файла — только здесь, в метаданных. Клинические PDF пациента сюда не попадают.
CREATE SCHEMA IF NOT EXISTS drive;

CREATE TABLE drive.drive_node (
  id UUID PRIMARY KEY,
  clinic_id UUID NOT NULL,
  parent_id UUID REFERENCES drive.drive_node(id),
  kind VARCHAR(10) NOT NULL,
  name VARCHAR(255) NOT NULL,
  owner_id UUID NOT NULL,
  size_bytes BIGINT DEFAULT 0 NOT NULL,
  current_version INTEGER DEFAULT 0 NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
  deleted_at TIMESTAMP WITH TIME ZONE,
  deleted_by UUID,
  version BIGINT DEFAULT 0 NOT NULL,
  CONSTRAINT drive_node_kind_valid CHECK (kind IN ('FILE', 'FOLDER'))
);
CREATE INDEX drive_node_parent_idx ON drive.drive_node(clinic_id, parent_id);
CREATE INDEX drive_node_owner_idx ON drive.drive_node(clinic_id, owner_id);

CREATE TABLE drive.drive_version (
  id UUID PRIMARY KEY,
  node_id UUID NOT NULL REFERENCES drive.drive_node(id),
  version_no INTEGER NOT NULL,
  object_key VARCHAR(120) NOT NULL,
  size_bytes BIGINT NOT NULL,
  sha256 VARCHAR(64) NOT NULL,
  content_type VARCHAR(120),
  uploaded_by UUID NOT NULL,
  uploaded_at TIMESTAMP WITH TIME ZONE NOT NULL,
  CONSTRAINT drive_version_unique UNIQUE (node_id, version_no)
);

-- Право на узел; наследуется вложенными узлами и может их только расширять.
CREATE TABLE drive.drive_share (
  id UUID PRIMARY KEY,
  node_id UUID NOT NULL REFERENCES drive.drive_node(id),
  principal_type VARCHAR(10) NOT NULL,
  principal_id VARCHAR(80),
  permission VARCHAR(10) NOT NULL,
  expires_at TIMESTAMP WITH TIME ZONE,
  link_token_hash VARCHAR(64),
  created_by UUID NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  CONSTRAINT drive_share_principal_valid CHECK (principal_type IN ('USER', 'ROLE', 'CLINIC', 'LINK')),
  CONSTRAINT drive_share_permission_valid CHECK (permission IN ('VIEW', 'EDIT', 'MANAGE')),
  CONSTRAINT drive_share_link_complete CHECK (
    principal_type <> 'LINK' OR (link_token_hash IS NOT NULL AND expires_at IS NOT NULL))
);
CREATE INDEX drive_share_node_idx ON drive.drive_share(node_id);
CREATE INDEX drive_share_principal_idx ON drive.drive_share(principal_type, principal_id);
CREATE UNIQUE INDEX drive_share_link_idx ON drive.drive_share(link_token_hash);

CREATE TABLE drive.drive_quota (
  clinic_id UUID PRIMARY KEY,
  bytes_used BIGINT NOT NULL,
  bytes_limit BIGINT NOT NULL
);
