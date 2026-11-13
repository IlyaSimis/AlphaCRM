package ru.quietclinic.workspace;

import java.time.Instant;
import java.util.UUID;

public record SavedView(UUID id, String name, WorkspaceFilter filter, Instant createdAt) {}
