--liquibase formatted sql

--changeset quietclinic:002 dbms:postgresql
CREATE EXTENSION IF NOT EXISTS btree_gist;
ALTER TABLE appointment
  ADD CONSTRAINT appointment_no_overlap
  EXCLUDE USING gist (
    practitioner_id WITH =,
    tstzrange(starts_at, ends_at, '[)') WITH &&
  )
  WHERE (status IN ('BOOKED','CONFIRMED','ARRIVED','IN_PROGRESS'));

--changeset quietclinic:003 dbms:postgresql splitStatements:false
CREATE OR REPLACE FUNCTION prevent_locked_document_mutation()
RETURNS trigger AS $$
BEGIN
  IF OLD.state = 'SIGNED_LOCKED' AND (
    NEW.snapshot_encrypted IS DISTINCT FROM OLD.snapshot_encrypted
    OR NEW.content_hash IS DISTINCT FROM OLD.content_hash
    OR NEW.signed_by IS DISTINCT FROM OLD.signed_by
    OR NEW.signed_at IS DISTINCT FROM OLD.signed_at
  ) THEN
    RAISE EXCEPTION 'SIGNED_LOCKED document is immutable';
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;
CREATE TRIGGER medical_document_immutable
BEFORE UPDATE ON medical_document
FOR EACH ROW EXECUTE FUNCTION prevent_locked_document_mutation();
