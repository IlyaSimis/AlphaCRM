--liquibase formatted sql

--changeset quietclinic:016
-- Демо-логины из changeset 013 больше не входят в систему: сотрудников заводит администратор
-- клиники. Демо-врачи и пациенты остаются в расписании, но учётных записей у них нет.
-- Задачи, назначенные демо-логинам, были демо-данными и удаляются вместе с ними.
DELETE FROM registry_task WHERE assignee_id IN (
  SELECT id FROM staff_user WHERE username IN ('smirnova', 'kuznetsov', 'orlova', 'petrov', 'lebedeva')
    AND id IN ('00000000-0000-0000-0000-000000000901', '00000000-0000-0000-0000-000000000902',
               '00000000-0000-0000-0000-000000000903', '00000000-0000-0000-0000-000000000201',
               '00000000-0000-0000-0000-000000000202'));
DELETE FROM staff_user WHERE username IN ('smirnova', 'kuznetsov', 'orlova', 'petrov', 'lebedeva')
  AND id IN ('00000000-0000-0000-0000-000000000901', '00000000-0000-0000-0000-000000000902',
             '00000000-0000-0000-0000-000000000903', '00000000-0000-0000-0000-000000000201',
             '00000000-0000-0000-0000-000000000202');

--changeset quietclinic:017
-- Клиники. Сотрудники, врачи и пациенты принадлежат одной клинике; выборки ограничены ею.
CREATE TABLE clinic (
  id UUID PRIMARY KEY,
  name VARCHAR(255) NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
-- Первая клиника. Уже существующие врачи и пациенты (в том числе демо-данные) попадают в неё.
-- Название при первом старте берётся из CLINIC_NAME (ClinicBootstrap), вместе с администратором.
INSERT INTO clinic(id, name, created_at)
VALUES ('00000000-0000-0000-0000-00000000c001', 'Клиника', CURRENT_TIMESTAMP);

ALTER TABLE staff_user ADD COLUMN clinic_id UUID;
ALTER TABLE practitioner ADD COLUMN clinic_id UUID;
ALTER TABLE patient ADD COLUMN clinic_id UUID;
UPDATE staff_user SET clinic_id = CAST('00000000-0000-0000-0000-00000000c001' AS UUID) WHERE clinic_id IS NULL;
UPDATE practitioner SET clinic_id = CAST('00000000-0000-0000-0000-00000000c001' AS UUID) WHERE clinic_id IS NULL;
UPDATE patient SET clinic_id = CAST('00000000-0000-0000-0000-00000000c001' AS UUID) WHERE clinic_id IS NULL;
ALTER TABLE staff_user ALTER COLUMN clinic_id SET NOT NULL;
ALTER TABLE practitioner ALTER COLUMN clinic_id SET NOT NULL;
ALTER TABLE patient ALTER COLUMN clinic_id SET NOT NULL;
ALTER TABLE staff_user ADD CONSTRAINT staff_user_clinic_fk FOREIGN KEY (clinic_id) REFERENCES clinic(id);
ALTER TABLE practitioner ADD CONSTRAINT practitioner_clinic_fk FOREIGN KEY (clinic_id) REFERENCES clinic(id);
ALTER TABLE patient ADD CONSTRAINT patient_clinic_fk FOREIGN KEY (clinic_id) REFERENCES clinic(id);
CREATE INDEX staff_user_clinic_idx ON staff_user(clinic_id);
CREATE INDEX practitioner_clinic_idx ON practitioner(clinic_id);
CREATE INDEX patient_clinic_idx ON patient(clinic_id);

-- Учётная запись с временным паролем: при первом входе сотрудник обязан сменить пароль.
ALTER TABLE staff_user ADD COLUMN must_change_password BOOLEAN DEFAULT FALSE NOT NULL;

--changeset quietclinic:018
-- Архив подписанных документов: выборка по состоянию и подписавшему врачу.
CREATE INDEX medical_document_signed_idx ON medical_document(state, signed_by, signed_at);
