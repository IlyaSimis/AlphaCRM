--liquibase formatted sql

--changeset quietclinic:040
-- У задачи есть автор (owner_id) и исполнитель. Существующие личные записи остаются у автора.
ALTER TABLE workspace.item ADD COLUMN assignee_id UUID REFERENCES public.staff_user(id);
UPDATE workspace.item SET assignee_id = owner_id;
ALTER TABLE workspace.item ALTER COLUMN assignee_id SET NOT NULL;
CREATE INDEX workspace_item_assignee_idx ON workspace.item(clinic_id, assignee_id, archived_at, updated_at);
