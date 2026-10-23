--liquibase formatted sql

--changeset quietclinic:013
-- Учётные записи сотрудников. Id людей совпадают с прежними демо-идентификаторами,
-- у врача id учётной записи и practitioner_id одинаковые: журнал аудита и связь с лечением
-- продолжают показывать тех же людей.
CREATE TABLE staff_user (
  id UUID PRIMARY KEY,
  full_name VARCHAR(255) NOT NULL,
  username VARCHAR(80) NOT NULL UNIQUE,
  password_hash VARCHAR(100) NOT NULL,
  role VARCHAR(40) NOT NULL,
  practitioner_id UUID UNIQUE REFERENCES practitioner(id),
  active BOOLEAN NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  CONSTRAINT staff_user_role_valid CHECK (role IN ('ADMINISTRATOR', 'DOCTOR', 'MANAGER', 'CASHIER')),
  CONSTRAINT staff_user_practitioner_only_for_doctor CHECK (
    (role = 'DOCTOR' AND practitioner_id IS NOT NULL) OR (role <> 'DOCTOR' AND practitioner_id IS NULL))
);
-- Пароль локальной разработки у всех: clinic-dev. Профиль prod с этим хешем не стартует.
INSERT INTO staff_user(id, full_name, username, password_hash, role, practitioner_id, active, created_at) VALUES
('00000000-0000-0000-0000-000000000901', 'Елена Смирнова', 'smirnova', '$2a$10$SKx2MjIaAu2uTO/YEG/yUej5CJnnI2WEkSLmPme6BQlB2ene.8Kae', 'ADMINISTRATOR', NULL, TRUE, CURRENT_TIMESTAMP),
('00000000-0000-0000-0000-000000000902', 'Игорь Кузнецов', 'kuznetsov', '$2a$10$SKx2MjIaAu2uTO/YEG/yUej5CJnnI2WEkSLmPme6BQlB2ene.8Kae', 'MANAGER', NULL, TRUE, CURRENT_TIMESTAMP),
('00000000-0000-0000-0000-000000000903', 'Наталья Орлова', 'orlova', '$2a$10$SKx2MjIaAu2uTO/YEG/yUej5CJnnI2WEkSLmPme6BQlB2ene.8Kae', 'CASHIER', NULL, TRUE, CURRENT_TIMESTAMP),
('00000000-0000-0000-0000-000000000201', 'Александр Петров', 'petrov', '$2a$10$SKx2MjIaAu2uTO/YEG/yUej5CJnnI2WEkSLmPme6BQlB2ene.8Kae', 'DOCTOR', '00000000-0000-0000-0000-000000000201', TRUE, CURRENT_TIMESTAMP),
('00000000-0000-0000-0000-000000000202', 'Ольга Лебедева', 'lebedeva', '$2a$10$SKx2MjIaAu2uTO/YEG/yUej5CJnnI2WEkSLmPme6BQlB2ene.8Kae', 'DOCTOR', '00000000-0000-0000-0000-000000000202', TRUE, CURRENT_TIMESTAMP);

--changeset quietclinic:014
-- Административные поля регистратуры: в клинический контур не попадают.
ALTER TABLE patient ADD COLUMN referral_source VARCHAR(40);
ALTER TABLE patient ADD COLUMN registry_note VARCHAR(1000);
ALTER TABLE patient ADD CONSTRAINT patient_referral_source_valid CHECK (
  referral_source IS NULL
  OR referral_source IN ('RECOMMENDATION', 'WEBSITE', 'INSURANCE', 'DOCTOR_REFERRAL', 'OTHER'));

--changeset quietclinic:015
-- Задачи регистратуры. Это напоминания сотрудникам, а не отправка сообщений пациенту.
CREATE TABLE registry_task (
  id UUID PRIMARY KEY,
  patient_id UUID NOT NULL REFERENCES patient(id),
  kind VARCHAR(40) NOT NULL,
  due_date DATE NOT NULL,
  assignee_id UUID NOT NULL REFERENCES staff_user(id),
  status VARCHAR(20) NOT NULL,
  created_by UUID NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  done_at TIMESTAMP WITH TIME ZONE,
  CONSTRAINT registry_task_kind_valid CHECK (kind IN ('CALL_BACK', 'UPDATE_CONSENT')),
  CONSTRAINT registry_task_status_valid CHECK (status IN ('OPEN', 'DONE'))
);
CREATE INDEX registry_task_due_idx ON registry_task(status, due_date);
CREATE INDEX registry_task_patient_idx ON registry_task(patient_id);

-- Лист ожидания: пациент ждёт свободного слота у врача в желаемый период.
CREATE TABLE waitlist_entry (
  id UUID PRIMARY KEY,
  patient_id UUID NOT NULL REFERENCES patient(id),
  practitioner_id UUID NOT NULL REFERENCES practitioner(id),
  service_id UUID NOT NULL REFERENCES clinic_service(id),
  wish_from DATE NOT NULL,
  wish_to DATE NOT NULL,
  status VARCHAR(20) NOT NULL,
  appointment_id UUID REFERENCES appointment(id),
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  CONSTRAINT waitlist_period_valid CHECK (wish_to >= wish_from),
  CONSTRAINT waitlist_status_valid CHECK (status IN ('ACTIVE', 'BOOKED', 'CANCELLED'))
);
CREATE INDEX waitlist_active_idx ON waitlist_entry(status, practitioner_id, wish_from, wish_to);

INSERT INTO registry_task(id, patient_id, kind, due_date, assignee_id, status, created_by, created_at) VALUES
('00000000-0000-0000-0000-000000000701', '00000000-0000-0000-0000-000000000103', 'UPDATE_CONSENT', DATE '2026-10-20', '00000000-0000-0000-0000-000000000901', 'OPEN', '00000000-0000-0000-0000-000000000901', CURRENT_TIMESTAMP);
INSERT INTO waitlist_entry(id, patient_id, practitioner_id, service_id, wish_from, wish_to, status, created_at) VALUES
('00000000-0000-0000-0000-000000000801', '00000000-0000-0000-0000-000000000103', '00000000-0000-0000-0000-000000000201', '00000000-0000-0000-0000-000000000602', DATE '2026-10-19', DATE '2026-10-30', 'ACTIVE', CURRENT_TIMESTAMP);
