--liquibase formatted sql

--changeset quietclinic:098
-- Минимальный остаток номенклатуры для складских алертов и метрик.
ALTER TABLE inventory.item ADD COLUMN min_stock NUMERIC(14, 3) DEFAULT 0 NOT NULL;
ALTER TABLE inventory.item ADD CONSTRAINT item_min_stock_nonnegative CHECK (min_stock >= 0);
