package ru.quietclinic.platform;

import java.time.Instant;
import java.util.Map;

record ApiError(
    Instant timestamp, int status, String code, String message, Map<String, String> fields) {}
