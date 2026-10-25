--liquibase formatted sql

--changeset quietclinic:025
-- Кабинет врача — подпись для экрана, не ресурс: не уникален, пустой хранится как NULL.
ALTER TABLE practitioner ADD COLUMN room VARCHAR(80);

--changeset quietclinic:026
-- Постоянный администратор пациента. E-mail и второй телефон — обычные строки patient_contact
-- с типами EMAIL и SECOND_PHONE, отдельно от основного PHONE; новых столбцов для них нет.
ALTER TABLE patient ADD COLUMN assigned_staff_id UUID;
ALTER TABLE patient ADD CONSTRAINT patient_assigned_staff_fk
  FOREIGN KEY (assigned_staff_id) REFERENCES staff_user(id);
CREATE INDEX patient_assigned_staff_idx ON patient(assigned_staff_id);

--changeset quietclinic:027
-- PDF пациента. В строке — только шифртекст (AES-GCM, тот же ключ KMS, что у заметок приёма);
-- имя без пути. Хеш SHA-256 открытых байтов считается при подписи. Удаления нет: замена —
-- новый файл.
CREATE TABLE patient_file (
  id UUID PRIMARY KEY,
  clinic_id UUID NOT NULL REFERENCES clinic(id),
  patient_id UUID NOT NULL REFERENCES patient(id),
  appointment_id UUID REFERENCES appointment(id),
  original_name VARCHAR(255) NOT NULL,
  size_bytes BIGINT NOT NULL,
  ciphertext BYTEA NOT NULL,
  state VARCHAR(40) NOT NULL,
  content_hash VARCHAR(64),
  uploaded_by UUID NOT NULL REFERENCES staff_user(id),
  uploaded_at TIMESTAMP WITH TIME ZONE NOT NULL,
  signed_by UUID REFERENCES staff_user(id),
  signed_at TIMESTAMP WITH TIME ZONE,
  CONSTRAINT patient_file_state_valid CHECK (state IN ('DRAFT', 'SIGNED_LOCKED')),
  CONSTRAINT patient_file_size_valid CHECK (size_bytes > 0 AND size_bytes <= 10485760),
  CONSTRAINT patient_file_signed_complete CHECK (
    state = 'DRAFT' OR (content_hash IS NOT NULL AND signed_by IS NOT NULL AND signed_at IS NOT NULL))
);
CREATE INDEX patient_file_patient_idx ON patient_file(clinic_id, patient_id, uploaded_at);
CREATE INDEX patient_file_signed_idx ON patient_file(clinic_id, state, signed_by);

--changeset quietclinic:028 dbms:postgresql splitStatements:false
-- Подписанный файл неизменяем и на уровне базы, как medical_document: байты, имя, размер, хеш,
-- подписант и время подписи; вернуть его в черновик тоже нельзя. Удаление запрещено всегда.
CREATE OR REPLACE FUNCTION prevent_signed_file_mutation()
RETURNS trigger AS $$
BEGIN
  IF TG_OP = 'DELETE' THEN
    RAISE EXCEPTION 'patient_file rows are never deleted';
  END IF;
  IF OLD.state = 'SIGNED_LOCKED' AND (
    NEW.state IS DISTINCT FROM OLD.state
    OR NEW.ciphertext IS DISTINCT FROM OLD.ciphertext
    OR NEW.original_name IS DISTINCT FROM OLD.original_name
    OR NEW.size_bytes IS DISTINCT FROM OLD.size_bytes
    OR NEW.content_hash IS DISTINCT FROM OLD.content_hash
    OR NEW.signed_by IS DISTINCT FROM OLD.signed_by
    OR NEW.signed_at IS DISTINCT FROM OLD.signed_at
    OR NEW.patient_id IS DISTINCT FROM OLD.patient_id
  ) THEN
    RAISE EXCEPTION 'SIGNED_LOCKED patient_file is immutable';
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;
CREATE TRIGGER patient_file_immutable
BEFORE UPDATE OR DELETE ON patient_file
FOR EACH ROW EXECUTE FUNCTION prevent_signed_file_mutation();
