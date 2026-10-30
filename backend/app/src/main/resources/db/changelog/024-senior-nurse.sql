--liquibase formatted sql

--changeset quietclinic:095
-- Отдельная роль старшего медперсонала: права назначаются в RolePermissions.
ALTER TABLE staff_user DROP CONSTRAINT staff_user_role_valid;
ALTER TABLE staff_user ADD CONSTRAINT staff_user_role_valid CHECK (role IN (
  'OWNER', 'SENIOR_ADMINISTRATOR', 'ADMINISTRATOR', 'MANAGER', 'DOCTOR', 'NURSE', 'SENIOR_NURSE', 'CASHIER', 'SUPPORT'));
ALTER TABLE staff_role DROP CONSTRAINT staff_role_valid;
ALTER TABLE staff_role ADD CONSTRAINT staff_role_valid CHECK (role IN (
  'OWNER', 'SENIOR_ADMINISTRATOR', 'ADMINISTRATOR', 'MANAGER', 'DOCTOR', 'NURSE', 'SENIOR_NURSE', 'CASHIER', 'SUPPORT'));
