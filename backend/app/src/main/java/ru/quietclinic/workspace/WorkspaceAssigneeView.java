package ru.quietclinic.workspace;

import java.util.UUID;

/** Безопасный элемент справочника исполнителей: без логина, пароля и служебных разрешений. */
public record WorkspaceAssigneeView(UUID id, String fullName, String role) {}
