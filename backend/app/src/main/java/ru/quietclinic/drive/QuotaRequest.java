package ru.quietclinic.drive;

import jakarta.validation.constraints.Positive;

record QuotaRequest(@Positive long bytesLimit) {}
