package ru.quietclinic.tenancy;

import java.util.UUID;

/** Публичная часть маршрута клиники без URL и credentials её базы. */
public record TenantRoute(UUID id, String name, String status) {}
