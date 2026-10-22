--liquibase formatted sql

--changeset quietclinic:007
-- Справочник услуг: длительность и цена в копейках.
CREATE TABLE clinic_service (
  id UUID PRIMARY KEY,
  name VARCHAR(255) NOT NULL UNIQUE,
  duration_minutes INTEGER NOT NULL,
  price_minor BIGINT NOT NULL,
  currency VARCHAR(3) NOT NULL,
  CONSTRAINT clinic_service_duration_positive CHECK (duration_minutes > 0),
  CONSTRAINT clinic_service_price_not_negative CHECK (price_minor >= 0)
);
INSERT INTO clinic_service(id, name, duration_minutes, price_minor, currency) VALUES
('00000000-0000-0000-0000-000000000601', 'Первичный приём', 50, 450000, 'RUB'),
('00000000-0000-0000-0000-000000000602', 'Повторный приём', 30, 300000, 'RUB');

--changeset quietclinic:008
-- Снимок цены на записи: дальнейшее изменение прайса не меняет уже назначенные приёмы.
ALTER TABLE appointment ADD COLUMN amount_minor BIGINT;
ALTER TABLE appointment ADD COLUMN currency VARCHAR(3);
UPDATE appointment SET
  amount_minor = (SELECT s.price_minor FROM clinic_service s WHERE s.name = appointment.service_name),
  currency = (SELECT s.currency FROM clinic_service s WHERE s.name = appointment.service_name);

--changeset quietclinic:009
-- Один счёт на приём.
CREATE UNIQUE INDEX charge_appointment_unique ON charge(appointment_id);

--changeset quietclinic:010 dbms:h2
-- Связь лечения для уже существующих записей: раньше её создавали только вручную.
INSERT INTO care_relationship(id, practitioner_id, patient_id, valid_from, valid_until, reason)
SELECT RANDOM_UUID(), pairs.practitioner_id, pairs.patient_id, CURRENT_TIMESTAMP,
       DATEADD('YEAR', 1, CURRENT_TIMESTAMP), 'Назначение по записи'
FROM (SELECT DISTINCT practitioner_id, patient_id FROM appointment WHERE status <> 'CANCELLED') pairs
WHERE NOT EXISTS (
  SELECT 1 FROM care_relationship c
  WHERE c.practitioner_id = pairs.practitioner_id AND c.patient_id = pairs.patient_id
    AND c.valid_until > CURRENT_TIMESTAMP);

--changeset quietclinic:011 dbms:postgresql
INSERT INTO care_relationship(id, practitioner_id, patient_id, valid_from, valid_until, reason)
SELECT gen_random_uuid(), pairs.practitioner_id, pairs.patient_id, CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP + INTERVAL '1 year', 'Назначение по записи'
FROM (SELECT DISTINCT practitioner_id, patient_id FROM appointment WHERE status <> 'CANCELLED') pairs
WHERE NOT EXISTS (
  SELECT 1 FROM care_relationship c
  WHERE c.practitioner_id = pairs.practitioner_id AND c.patient_id = pairs.patient_id
    AND c.valid_until > CURRENT_TIMESTAMP);

--changeset quietclinic:012
-- Порядок версий документа приёма (оригинал → дополнения). Триггер неизменяемости этих полей не касается.
ALTER TABLE medical_document ADD COLUMN created_at TIMESTAMP WITH TIME ZONE;
UPDATE medical_document SET created_at = COALESCE(signed_at, CURRENT_TIMESTAMP);
