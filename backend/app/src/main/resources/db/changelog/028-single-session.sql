--liquibase formatted sql

--changeset quietclinic:048
-- Один сотрудник — одна активная сессия. Сама сессия в prod хранится в SPRING_SESSION.
ALTER TABLE staff_user ADD COLUMN current_session_id VARCHAR(36);

--changeset quietclinic:049 dbms:postgresql
-- Spring Session JDBC: схема управляется Liquibase, автоматическое создание выключено.
CREATE TABLE spring_session (
  primary_id CHAR(36) NOT NULL,
  session_id CHAR(36) NOT NULL,
  creation_time BIGINT NOT NULL,
  last_access_time BIGINT NOT NULL,
  max_inactive_interval INTEGER NOT NULL,
  expiry_time BIGINT NOT NULL,
  principal_name VARCHAR(100),
  CONSTRAINT spring_session_pk PRIMARY KEY (primary_id)
);
CREATE UNIQUE INDEX spring_session_ix1 ON spring_session (session_id);
CREATE INDEX spring_session_ix2 ON spring_session (expiry_time);
CREATE INDEX spring_session_ix3 ON spring_session (principal_name);

CREATE TABLE spring_session_attributes (
  session_primary_id CHAR(36) NOT NULL,
  attribute_name VARCHAR(200) NOT NULL,
  attribute_bytes BYTEA NOT NULL,
  CONSTRAINT spring_session_attributes_pk PRIMARY KEY (session_primary_id, attribute_name),
  CONSTRAINT spring_session_attributes_fk FOREIGN KEY (session_primary_id)
    REFERENCES spring_session(primary_id) ON DELETE CASCADE
);

--changeset quietclinic:050 dbms:h2
-- Тот же контракт для локального PostgreSQL-совместимого H2.
CREATE TABLE spring_session (
  primary_id CHAR(36) NOT NULL,
  session_id CHAR(36) NOT NULL,
  creation_time BIGINT NOT NULL,
  last_access_time BIGINT NOT NULL,
  max_inactive_interval INTEGER NOT NULL,
  expiry_time BIGINT NOT NULL,
  principal_name VARCHAR(100),
  CONSTRAINT spring_session_pk PRIMARY KEY (primary_id)
);
CREATE UNIQUE INDEX spring_session_ix1 ON spring_session (session_id);
CREATE INDEX spring_session_ix2 ON spring_session (expiry_time);
CREATE INDEX spring_session_ix3 ON spring_session (principal_name);

CREATE TABLE spring_session_attributes (
  session_primary_id CHAR(36) NOT NULL,
  attribute_name VARCHAR(200) NOT NULL,
  attribute_bytes LONGVARBINARY NOT NULL,
  CONSTRAINT spring_session_attributes_pk PRIMARY KEY (session_primary_id, attribute_name),
  CONSTRAINT spring_session_attributes_fk FOREIGN KEY (session_primary_id)
    REFERENCES spring_session(primary_id) ON DELETE CASCADE
);
