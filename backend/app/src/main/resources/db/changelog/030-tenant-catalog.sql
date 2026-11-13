-- Управляющий каталог используется только через controlDataSource. Те же таблицы могут существовать
-- в tenant БД после прогона общего changelog, но доменные запросы к ним не обращаются.
create table tenant_catalog (
  id uuid primary key,
  name varchar(200) not null,
  status varchar(20) not null,
  database_url text not null,
  database_username varchar(200) not null,
  password_ciphertext bytea not null,
  wrapped_data_key bytea not null,
  key_version varchar(100) not null,
  created_at TIMESTAMP WITH TIME ZONE not null
);

create table tenant_login (
  username varchar(320) primary key,
  tenant_id uuid not null,
  created_at TIMESTAMP WITH TIME ZONE not null
);
create index tenant_login_tenant_idx on tenant_login(tenant_id);

create table tenant_webhook (
  public_key uuid not null,
  type varchar(30) not null,
  tenant_id uuid not null,
  primary key(public_key, type)
);
create index tenant_webhook_tenant_idx on tenant_webhook(tenant_id);
