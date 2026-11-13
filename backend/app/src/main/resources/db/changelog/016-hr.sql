--liquibase formatted sql

--changeset quietclinic:038
-- Условия работы сотрудника (раздел E, identity.hr_contract): должность и ставка с датой начала.
-- Новое условие закрывает прежнее днём раньше — история сохраняется. Чужие видит и меняет только
-- владелец, свои — каждый сотрудник (раздел C, hr). Суммы в журнал аудита не пишутся.
CREATE TABLE hr_contract (
  id UUID PRIMARY KEY,
  clinic_id UUID NOT NULL REFERENCES clinic(id),
  staff_id UUID NOT NULL REFERENCES staff_user(id),
  position VARCHAR(160) NOT NULL,
  rate_minor BIGINT NOT NULL,
  rate_unit VARCHAR(10) NOT NULL,
  currency VARCHAR(3) NOT NULL,
  valid_from DATE NOT NULL,
  valid_to DATE,
  created_by UUID NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  CONSTRAINT hr_contract_rate_valid CHECK (rate_minor >= 0),
  CONSTRAINT hr_contract_unit_valid CHECK (rate_unit IN ('MONTH', 'HOUR', 'VISIT')),
  CONSTRAINT hr_contract_period_valid CHECK (valid_to IS NULL OR valid_to >= valid_from)
);
CREATE INDEX hr_contract_staff_idx ON hr_contract(staff_id, valid_from);
