--liquibase formatted sql

--changeset quietclinic:037
-- Несколько ролей на сотрудника (раздел C: «одна или несколько ролей в клинике»): права
-- складываются. staff_user.role остаётся основной ролью — у врача это DOCTOR (с карточкой врача).
CREATE TABLE staff_role (
  staff_id UUID NOT NULL REFERENCES staff_user(id),
  role VARCHAR(40) NOT NULL,
  PRIMARY KEY (staff_id, role),
  CONSTRAINT staff_role_valid CHECK (role IN (
    'OWNER', 'SENIOR_ADMINISTRATOR', 'ADMINISTRATOR', 'MANAGER', 'DOCTOR', 'NURSE', 'CASHIER', 'SUPPORT'))
);
INSERT INTO staff_role (staff_id, role) SELECT id, role FROM staff_user;
CREATE INDEX staff_role_role_idx ON staff_role(role);
