package ru.quietclinic.tenancy;

import java.util.UUID;

/** Создаёт физическую базу и отдельного DB-пользователя клиники. */
interface TenantDatabaseManager {
  TenantDatabase create(UUID tenantId);
}
