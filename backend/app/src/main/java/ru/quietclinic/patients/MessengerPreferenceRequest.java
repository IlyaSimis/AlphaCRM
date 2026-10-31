package ru.quietclinic.patients;

import jakarta.validation.constraints.Pattern;

/** null или пустая строка — автоматический выбор доступного канала. */
record MessengerPreferenceRequest(
    @Pattern(regexp = "^\\s*(?i:(TELEGRAM|MAX))?\\s*$") String channel) {}
