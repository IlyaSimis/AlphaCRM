--liquibase formatted sql

--changeset quietclinic:090 dbms:postgresql
-- Расширенные административные реквизиты пациента. Клинические записи остаются в отдельном контуре.
ALTER TABLE patient ADD COLUMN profile JSONB;

--changeset quietclinic:091 dbms:h2
-- Локальная разработка и тесты используют тот же JSON-документ в H2.
ALTER TABLE patient ADD COLUMN profile JSON;
