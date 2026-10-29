--liquibase formatted sql

--changeset quietclinic:040
-- Подписка клиники отделена от счетов пациентов. Вопросы навигатора не хранятся: в учёте только
-- количество обращений и токенов за календарный месяц UTC.
CREATE SCHEMA IF NOT EXISTS commercial;
CREATE SCHEMA IF NOT EXISTS assistant;

CREATE TABLE commercial.subscription (
  clinic_id UUID PRIMARY KEY REFERENCES public.clinic(id),
  plan_code VARCHAR(30) NOT NULL,
  current_period_end TIMESTAMP WITH TIME ZONE,
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE commercial.payment (
  id UUID PRIMARY KEY,
  clinic_id UUID NOT NULL REFERENCES public.clinic(id),
  plan_code VARCHAR(30) NOT NULL,
  amount_minor BIGINT NOT NULL,
  currency VARCHAR(3) NOT NULL,
  status VARCHAR(30) NOT NULL,
  provider VARCHAR(30) NOT NULL,
  provider_ref VARCHAR(120),
  idempotency_key VARCHAR(80) NOT NULL UNIQUE,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
  paid_at TIMESTAMP WITH TIME ZONE
);
CREATE UNIQUE INDEX commercial_payment_provider_ref_idx ON commercial.payment(provider, provider_ref);
CREATE INDEX commercial_payment_clinic_idx ON commercial.payment(clinic_id, created_at);

CREATE TABLE assistant.usage_counter (
  clinic_id UUID NOT NULL REFERENCES public.clinic(id),
  usage_month VARCHAR(7) NOT NULL,
  request_count INTEGER NOT NULL,
  input_tokens BIGINT NOT NULL,
  output_tokens BIGINT NOT NULL,
  PRIMARY KEY (clinic_id, usage_month)
);
