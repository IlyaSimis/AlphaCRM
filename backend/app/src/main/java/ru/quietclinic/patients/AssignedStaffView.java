package ru.quietclinic.patients;

import java.util.UUID;

/**
 * Постоянный администратор пациента. {@code active=false} — учётная запись отключена: связь
 * осталась, но в новых выборах этого сотрудника нет.
 */
record AssignedStaffView(UUID id, String fullName, boolean active) {}
