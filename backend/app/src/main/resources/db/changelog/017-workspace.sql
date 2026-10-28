--liquibase formatted sql

--changeset quietclinic:039
-- Личное рабочее пространство: тип, статус, теги, произвольные поля и чек-лист не ограничены
-- справочниками приложения. Содержимое принадлежит сотруднику и не входит в клиническую историю.
CREATE SCHEMA IF NOT EXISTS workspace;

CREATE TABLE workspace.item (
  id UUID PRIMARY KEY,
  clinic_id UUID NOT NULL REFERENCES public.clinic(id),
  owner_id UUID NOT NULL REFERENCES public.staff_user(id),
  type VARCHAR(60) NOT NULL,
  title VARCHAR(240) NOT NULL,
  body VARCHAR(30000),
  status VARCHAR(80) NOT NULL,
  color VARCHAR(20) NOT NULL,
  completed BOOLEAN DEFAULT FALSE NOT NULL,
  due_at TIMESTAMP WITH TIME ZONE,
  remind_at TIMESTAMP WITH TIME ZONE,
  patient_id UUID,
  appointment_id UUID,
  tags VARCHAR(12000) NOT NULL,
  fields VARCHAR(30000) NOT NULL,
  checklist VARCHAR(30000) NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
  completed_at TIMESTAMP WITH TIME ZONE,
  archived_at TIMESTAMP WITH TIME ZONE
);
CREATE INDEX workspace_item_owner_idx ON workspace.item(owner_id, archived_at, updated_at);
CREATE INDEX workspace_item_clinic_idx ON workspace.item(clinic_id, owner_id);
CREATE INDEX workspace_item_due_idx ON workspace.item(owner_id, completed, due_at);
CREATE INDEX workspace_item_patient_idx ON workspace.item(clinic_id, patient_id);

CREATE TABLE workspace.saved_view (
  id UUID PRIMARY KEY,
  clinic_id UUID NOT NULL REFERENCES public.clinic(id),
  owner_id UUID NOT NULL REFERENCES public.staff_user(id),
  name VARCHAR(100) NOT NULL,
  filter_json VARCHAR(8000) NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  CONSTRAINT workspace_saved_view_name_unique UNIQUE (owner_id, name)
);
CREATE INDEX workspace_saved_view_owner_idx ON workspace.saved_view(owner_id, name);
