package ru.quietclinic.drive;

import java.time.Instant;
import java.util.UUID;

record VersionView(
    int version,
    long sizeBytes,
    String sha256,
    String contentType,
    UUID uploadedBy,
    String uploadedByName,
    Instant uploadedAt) {}
