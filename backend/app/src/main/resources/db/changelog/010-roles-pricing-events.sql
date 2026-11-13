--liquibase formatted sql

--changeset quietclinic:029
-- Роли целевой архитектуры (docs/target-architecture.md, раздел C): владелец, старший
-- администратор, медицинский персонал, техподдержка. Разрешения ролей — в коде (RolePermissions).
ALTER TABLE staff_user DROP CONSTRAINT staff_user_role_valid;
ALTER TABLE staff_user ADD CONSTRAINT staff_user_role_valid CHECK (role IN (
  'OWNER', 'SENIOR_ADMINISTRATOR', 'ADMINISTRATOR', 'MANAGER', 'DOCTOR', 'NURSE', 'CASHIER', 'SUPPORT'));
-- Начальный администратор (логин admin из ClinicBootstrap) — первый человек клиники: он становится
-- её владельцем, если владельца ещё нет. Новые установки сразу создают владельца.
UPDATE staff_user SET role = 'OWNER'
WHERE username = 'admin' AND role = 'ADMINISTRATOR'
  AND NOT EXISTS (SELECT 1 FROM staff_user o WHERE o.clinic_id = staff_user.clinic_id AND o.role = 'OWNER');

--changeset quietclinic:030
-- Свободная цена: услуга с режимом OPEN не имеет фиксированной цены, только пределы min/max;
-- сумму вводят при выставлении счёта или оплате.
ALTER TABLE clinic_service ADD COLUMN price_mode VARCHAR(10) DEFAULT 'FIXED' NOT NULL;
ALTER TABLE clinic_service ADD COLUMN min_minor BIGINT;
ALTER TABLE clinic_service ADD COLUMN max_minor BIGINT;
ALTER TABLE clinic_service ALTER COLUMN price_minor DROP NOT NULL;
ALTER TABLE clinic_service ADD CONSTRAINT clinic_service_price_mode_valid CHECK (price_mode IN ('FIXED', 'OPEN'));
ALTER TABLE clinic_service ADD CONSTRAINT clinic_service_pricing_valid CHECK (
  (price_mode = 'FIXED' AND price_minor IS NOT NULL)
  OR (price_mode = 'OPEN' AND price_minor IS NULL AND min_minor IS NOT NULL AND max_minor IS NOT NULL
      AND min_minor >= 0 AND max_minor >= min_minor));

-- Запись хранит снимок режима цены и ссылку на услугу; при свободной цене amount_minor пуст,
-- estimate_minor — необязательная предварительная оценка.
ALTER TABLE appointment ADD COLUMN service_id UUID;
ALTER TABLE appointment ADD COLUMN price_mode VARCHAR(10);
ALTER TABLE appointment ADD COLUMN estimate_minor BIGINT;
UPDATE appointment SET service_id = (
  SELECT s.id FROM clinic_service s JOIN practitioner p ON p.clinic_id = s.clinic_id
  WHERE p.id = appointment.practitioner_id AND s.name = appointment.service_name);
UPDATE appointment SET price_mode = 'FIXED' WHERE amount_minor IS NOT NULL;
CREATE INDEX appointment_service_idx ON appointment(service_id);

-- Счёт по свободной цене ждёт суммы (AWAITING_AMOUNT); пределы — снимок услуги на момент приёма.
ALTER TABLE charge ALTER COLUMN amount_minor DROP NOT NULL;
ALTER TABLE charge ADD COLUMN price_mode VARCHAR(10) DEFAULT 'FIXED' NOT NULL;
ALTER TABLE charge ADD COLUMN min_minor BIGINT;
ALTER TABLE charge ADD COLUMN max_minor BIGINT;
ALTER TABLE charge ADD COLUMN estimate_minor BIGINT;
ALTER TABLE charge ADD CONSTRAINT charge_status_valid CHECK (status IN ('AWAITING_AMOUNT', 'PENDING', 'PAID'));
ALTER TABLE charge ADD CONSTRAINT charge_amount_present CHECK (status = 'AWAITING_AMOUNT' OR amount_minor IS NOT NULL);

--changeset quietclinic:031
-- Доменные события (раздел E, «Каталог событий»): конверт пишется в той же транзакции, что
-- изменение (transactional outbox); потребители отмечают обработку в inbox по eventId, поэтому
-- повтор доставки безопасен. В событиях — только идентификаторы и факты, без клинического текста.
CREATE TABLE domain_event_outbox (
  id UUID PRIMARY KEY,
  type VARCHAR(80) NOT NULL,
  version INTEGER NOT NULL,
  clinic_id UUID NOT NULL REFERENCES clinic(id),
  occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
  actor_id UUID NOT NULL,
  correlation_id VARCHAR(80),
  payload VARCHAR(8000) NOT NULL,
  status VARCHAR(20) NOT NULL,
  attempts INTEGER DEFAULT 0 NOT NULL,
  next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL,
  last_error VARCHAR(120),
  published_at TIMESTAMP WITH TIME ZONE,
  CONSTRAINT domain_event_outbox_status_valid CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED'))
);
CREATE INDEX domain_event_outbox_due_idx ON domain_event_outbox(status, next_attempt_at);
CREATE INDEX domain_event_outbox_clinic_idx ON domain_event_outbox(clinic_id, occurred_at);

CREATE TABLE domain_event_inbox (
  consumer VARCHAR(80) NOT NULL,
  event_id UUID NOT NULL,
  processed_at TIMESTAMP WITH TIME ZONE NOT NULL,
  PRIMARY KEY (consumer, event_id)
);
