--liquibase formatted sql

--changeset quietclinic:032
-- Нормы расхода услуги (раздел E, schedule.clinic_service.consumption_norms): позиция склада и
-- количество на один приём. Позицию склада расписание знает только по id — без внешнего ключа
-- в схему склада.
CREATE TABLE clinic_service_norm (
  id UUID PRIMARY KEY,
  service_id UUID NOT NULL REFERENCES clinic_service(id),
  item_id UUID NOT NULL,
  quantity NUMERIC(14, 3) NOT NULL,
  CONSTRAINT clinic_service_norm_quantity_positive CHECK (quantity > 0),
  CONSTRAINT clinic_service_norm_unique UNIQUE (service_id, item_id)
);

--changeset quietclinic:033
-- Склад (раздел E, inventory) — сразу в своей схеме: модуль не читает чужие таблицы; его таблицы
-- читает только дашборд v1 (insights) — счётчики без строк. Пациентов здесь нет: расход по приёму хранит только appointment_id.
CREATE SCHEMA IF NOT EXISTS inventory;

CREATE TABLE inventory.item_category (
  id UUID PRIMARY KEY,
  clinic_id UUID NOT NULL,
  name VARCHAR(160) NOT NULL,
  parent_id UUID REFERENCES inventory.item_category(id),
  kind VARCHAR(20) NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  CONSTRAINT item_category_kind_valid CHECK (kind IN ('DRUG', 'MEDICAL_DEVICE', 'CONSUMABLE', 'GOODS'))
);
CREATE INDEX item_category_clinic_idx ON inventory.item_category(clinic_id);

CREATE TABLE inventory.item (
  id UUID PRIMARY KEY,
  clinic_id UUID NOT NULL,
  category_id UUID NOT NULL REFERENCES inventory.item_category(id),
  name VARCHAR(255) NOT NULL,
  unit VARCHAR(20) NOT NULL,
  gtin VARCHAR(14),
  marked BOOLEAN NOT NULL,
  marking_system VARCHAR(10) NOT NULL,
  rx_only BOOLEAN DEFAULT FALSE NOT NULL,
  active BOOLEAN DEFAULT TRUE NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  CONSTRAINT item_marking_system_valid CHECK (marking_system IN ('NONE', 'MDLP', 'GIS_MT')),
  CONSTRAINT item_marking_consistent CHECK (
    (marked = FALSE AND marking_system = 'NONE') OR (marked = TRUE AND marking_system <> 'NONE' AND gtin IS NOT NULL))
);
CREATE INDEX item_clinic_idx ON inventory.item(clinic_id, category_id);
CREATE INDEX item_gtin_idx ON inventory.item(clinic_id, gtin);

CREATE TABLE inventory.warehouse (
  id UUID PRIMARY KEY,
  clinic_id UUID NOT NULL,
  name VARCHAR(160) NOT NULL,
  is_default BOOLEAN DEFAULT FALSE NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX warehouse_clinic_idx ON inventory.warehouse(clinic_id);

-- Накладная: черновик правится, проведённая — нет (исправление — новым документом).
CREATE TABLE inventory.stock_document (
  id UUID PRIMARY KEY,
  clinic_id UUID NOT NULL,
  type VARCHAR(30) NOT NULL,
  status VARCHAR(20) NOT NULL,
  document_date DATE NOT NULL,
  supplier VARCHAR(255),
  appointment_id UUID,
  from_warehouse_id UUID REFERENCES inventory.warehouse(id),
  to_warehouse_id UUID REFERENCES inventory.warehouse(id),
  note VARCHAR(500),
  idempotency_key VARCHAR(160) NOT NULL,
  created_by UUID NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  posted_by UUID,
  posted_at TIMESTAMP WITH TIME ZONE,
  version BIGINT DEFAULT 0 NOT NULL,
  CONSTRAINT stock_document_type_valid CHECK (type IN ('RECEIPT', 'CONSUMPTION', 'WRITE_OFF', 'TRANSFER', 'RETURN_TO_SUPPLIER')),
  CONSTRAINT stock_document_status_valid CHECK (status IN ('DRAFT', 'POSTED', 'CANCELLED')),
  CONSTRAINT stock_document_idempotency_unique UNIQUE (clinic_id, idempotency_key)
);
CREATE INDEX stock_document_clinic_idx ON inventory.stock_document(clinic_id, document_date);
CREATE INDEX stock_document_appointment_idx ON inventory.stock_document(appointment_id);

CREATE TABLE inventory.stock_line (
  id UUID PRIMARY KEY,
  document_id UUID NOT NULL REFERENCES inventory.stock_document(id),
  line_no INTEGER NOT NULL,
  item_id UUID NOT NULL REFERENCES inventory.item(id),
  quantity NUMERIC(14, 3) NOT NULL,
  batch VARCHAR(40) DEFAULT '' NOT NULL,
  expires_on DATE,
  CONSTRAINT stock_line_quantity_positive CHECK (quantity > 0)
);
CREATE INDEX stock_line_document_idx ON inventory.stock_line(document_id);

-- Марки (SGTIN), отсканированные в строке накладной.
CREATE TABLE inventory.stock_line_code (
  line_id UUID NOT NULL REFERENCES inventory.stock_line(id),
  sgtin VARCHAR(40) NOT NULL,
  PRIMARY KEY (line_id, sgtin)
);

-- Остаток материализуется проведением накладных.
CREATE TABLE inventory.stock_balance (
  clinic_id UUID NOT NULL,
  item_id UUID NOT NULL REFERENCES inventory.item(id),
  warehouse_id UUID NOT NULL REFERENCES inventory.warehouse(id),
  batch VARCHAR(40) DEFAULT '' NOT NULL,
  expires_on DATE,
  quantity NUMERIC(14, 3) NOT NULL,
  PRIMARY KEY (item_id, warehouse_id, batch)
);
CREATE INDEX stock_balance_clinic_idx ON inventory.stock_balance(clinic_id);

-- Марка упаковки. Криптохвост кода не хранится.
CREATE TABLE inventory.marking_code (
  id UUID PRIMARY KEY,
  clinic_id UUID NOT NULL,
  sgtin VARCHAR(40) NOT NULL,
  gtin VARCHAR(14) NOT NULL,
  serial VARCHAR(20) NOT NULL,
  item_id UUID NOT NULL REFERENCES inventory.item(id),
  batch VARCHAR(40) DEFAULT '' NOT NULL,
  expires_on DATE,
  status VARCHAR(30) NOT NULL,
  warehouse_id UUID REFERENCES inventory.warehouse(id),
  document_id UUID NOT NULL REFERENCES inventory.stock_document(id),
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
  CONSTRAINT marking_code_status_valid CHECK (status IN ('PENDING_ACCEPTANCE', 'IN_STOCK', 'RESERVED',
    'WITHDRAWAL_PENDING', 'WITHDRAWN', 'WRITE_OFF_PENDING', 'WRITTEN_OFF', 'RETURN_PENDING', 'RETURNED', 'ERROR')),
  CONSTRAINT marking_code_unique UNIQUE (clinic_id, sgtin)
);
CREATE INDEX marking_code_status_idx ON inventory.marking_code(clinic_id, status);

-- Исходящие операции маркировки — outbox к оператору (МДЛП / ГИС МТ) через MarkingPort.
CREATE TABLE inventory.marking_operation (
  id UUID PRIMARY KEY,
  clinic_id UUID NOT NULL,
  type VARCHAR(30) NOT NULL,
  document_id UUID NOT NULL REFERENCES inventory.stock_document(id),
  status VARCHAR(20) NOT NULL,
  attempts INTEGER DEFAULT 0 NOT NULL,
  next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL,
  operator_ref VARCHAR(120),
  last_error VARCHAR(80),
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
  version BIGINT DEFAULT 0 NOT NULL,
  CONSTRAINT marking_operation_type_valid CHECK (type IN ('ACCEPTANCE', 'WITHDRAW_MEDICAL_USE', 'WRITE_OFF', 'RETURN_TO_SUPPLIER')),
  CONSTRAINT marking_operation_status_valid CHECK (status IN ('PENDING', 'DONE', 'ERROR'))
);
CREATE INDEX marking_operation_due_idx ON inventory.marking_operation(status, next_attempt_at);
CREATE INDEX marking_operation_clinic_idx ON inventory.marking_operation(clinic_id, created_at);

CREATE TABLE inventory.marking_operation_code (
  operation_id UUID NOT NULL REFERENCES inventory.marking_operation(id),
  sgtin VARCHAR(40) NOT NULL,
  PRIMARY KEY (operation_id, sgtin)
);
