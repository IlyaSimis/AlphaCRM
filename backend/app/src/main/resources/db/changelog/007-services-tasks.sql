--liquibase formatted sql

--changeset quietclinic:019
-- Справочник услуг ведёт администратор клиники: услуги принадлежат клинике и выключаются,
-- а не удаляются. Выключенная услуга не предлагается в новой записи; старые записи хранят
-- снимок названия и цены.
ALTER TABLE clinic_service ADD COLUMN clinic_id UUID;
UPDATE clinic_service SET clinic_id = '00000000-0000-0000-0000-00000000c001' WHERE clinic_id IS NULL;
ALTER TABLE clinic_service ALTER COLUMN clinic_id SET NOT NULL;
ALTER TABLE clinic_service ADD CONSTRAINT clinic_service_clinic_fk FOREIGN KEY (clinic_id) REFERENCES clinic(id);
ALTER TABLE clinic_service ADD COLUMN active BOOLEAN DEFAULT TRUE NOT NULL;
CREATE INDEX clinic_service_clinic_idx ON clinic_service(clinic_id, active);

--changeset quietclinic:020 dbms:postgresql
-- Название услуги уникально внутри клиники, а не во всей установке.
ALTER TABLE clinic_service DROP CONSTRAINT clinic_service_name_key;
CREATE UNIQUE INDEX clinic_service_clinic_name_unique ON clinic_service(clinic_id, name);

--changeset quietclinic:021
-- Задача регистратуры — свободный текст администратора, пациент необязателен. Прежние виды
-- задач становятся названием; задачи не удаляются. Клиника задачи хранится явно: задача без
-- пациента тоже принадлежит клинике.
ALTER TABLE registry_task ADD COLUMN title VARCHAR(200);
UPDATE registry_task SET title = CASE kind
  WHEN 'CALL_BACK' THEN 'Перезвонить'
  WHEN 'UPDATE_CONSENT' THEN 'Обновить согласие'
  ELSE kind END;
ALTER TABLE registry_task ALTER COLUMN title SET NOT NULL;
ALTER TABLE registry_task ADD COLUMN clinic_id UUID;
UPDATE registry_task SET clinic_id = (SELECT p.clinic_id FROM patient p WHERE p.id = registry_task.patient_id);
UPDATE registry_task SET clinic_id = '00000000-0000-0000-0000-00000000c001' WHERE clinic_id IS NULL;
ALTER TABLE registry_task ALTER COLUMN clinic_id SET NOT NULL;
ALTER TABLE registry_task ADD CONSTRAINT registry_task_clinic_fk FOREIGN KEY (clinic_id) REFERENCES clinic(id);
ALTER TABLE registry_task ALTER COLUMN patient_id DROP NOT NULL;
ALTER TABLE registry_task DROP CONSTRAINT registry_task_kind_valid;
ALTER TABLE registry_task DROP COLUMN kind;
CREATE INDEX registry_task_clinic_idx ON registry_task(clinic_id, status, due_date);

--changeset quietclinic:022
-- Основание связи с лечением — машинный код, как у новых связей (appointment-booking), а не
-- русская фраза. Текст для человека выбирает интерфейс.
UPDATE care_relationship SET reason = 'appointment-booking' WHERE reason IN ('Назначение по записи', 'Назначенный визит');
