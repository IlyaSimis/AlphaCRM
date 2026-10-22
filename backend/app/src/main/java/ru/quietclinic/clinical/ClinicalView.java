package ru.quietclinic.clinical;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

record ClinicalView(
    UUID encounterId,
    UUID appointmentId,
    UUID patientId,
    UUID practitionerId,
    String status,
    Instant startedAt,
    String complaints,
    String mentalStatus,
    String diagnosis,
    String recommendations,
    Long noteVersion,
    List<DocumentSummary> documents) {}
