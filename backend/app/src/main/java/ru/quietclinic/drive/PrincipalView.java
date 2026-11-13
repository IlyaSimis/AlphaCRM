package ru.quietclinic.drive;

import java.util.UUID;

/** Сотрудник клиники для диалога «Поделиться»: только ФИО и роль. */
record PrincipalView(UUID id, String fullName, String role) {}
