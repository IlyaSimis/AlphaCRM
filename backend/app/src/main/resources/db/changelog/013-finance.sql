--liquibase formatted sql

--changeset quietclinic:035
-- Финансы (раздел E, finance): аннулирование неоплаченного счёта и возврат оплаты. Причина —
-- машинный код из списка, без свободного текста; кто и когда — в строке и в журнале.
ALTER TABLE charge DROP CONSTRAINT charge_status_valid;
ALTER TABLE charge ADD CONSTRAINT charge_status_valid CHECK (
  status IN ('AWAITING_AMOUNT', 'PENDING', 'PAID', 'VOIDED', 'REFUNDED'));
ALTER TABLE charge DROP CONSTRAINT charge_amount_present;
ALTER TABLE charge ADD CONSTRAINT charge_amount_present CHECK (
  status IN ('AWAITING_AMOUNT', 'VOIDED') OR amount_minor IS NOT NULL);
ALTER TABLE charge ADD COLUMN voided_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE charge ADD COLUMN voided_by UUID;
ALTER TABLE charge ADD COLUMN void_reason VARCHAR(40);

-- Возврат — полный, один на платёж (частичный возврат — точка расширения). Чек возврата уходит
-- через порт кассы так же, как чек оплаты.
CREATE TABLE refund_record (
  id UUID PRIMARY KEY,
  payment_id UUID NOT NULL REFERENCES payment_record(id),
  charge_id UUID NOT NULL REFERENCES charge(id),
  idempotency_key VARCHAR(255) NOT NULL,
  amount_minor BIGINT NOT NULL,
  currency VARCHAR(3) NOT NULL,
  reason VARCHAR(40) NOT NULL,
  fiscal_status VARCHAR(40) NOT NULL,
  created_by UUID NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  CONSTRAINT refund_record_payment_unique UNIQUE (payment_id),
  CONSTRAINT refund_record_idempotency_unique UNIQUE (idempotency_key),
  CONSTRAINT refund_record_amount_positive CHECK (amount_minor > 0)
);
CREATE INDEX refund_record_created_idx ON refund_record(created_at);
CREATE INDEX payment_record_created_idx ON payment_record(created_at);
