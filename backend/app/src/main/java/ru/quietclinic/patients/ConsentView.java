package ru.quietclinic.patients;

import java.time.Instant;
import java.util.UUID;

record ConsentView(UUID id, String version, String basis, Instant grantedAt, Instant revokedAt) {}
