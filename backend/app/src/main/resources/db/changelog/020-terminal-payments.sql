--liquibase formatted sql

--changeset quietclinic:020
ALTER TABLE payment_record ADD COLUMN method VARCHAR(16) NOT NULL DEFAULT 'MANUAL';
ALTER TABLE payment_record ADD COLUMN completed_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE payment_record ADD COLUMN updated_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE payment_record ADD COLUMN failure_reason VARCHAR(255);
UPDATE payment_record SET updated_at = created_at WHERE updated_at IS NULL;
ALTER TABLE payment_record ALTER COLUMN updated_at SET NOT NULL;
CREATE UNIQUE INDEX payment_record_provider_ref_idx ON payment_record(provider_ref);

CREATE TABLE terminal_payment_event (
  id UUID PRIMARY KEY,
  provider VARCHAR(64) NOT NULL,
  event_id VARCHAR(128) NOT NULL,
  payment_id UUID NOT NULL REFERENCES payment_record(id),
  received_at TIMESTAMP WITH TIME ZONE NOT NULL,
  CONSTRAINT terminal_payment_event_unique UNIQUE(provider, event_id)
);
