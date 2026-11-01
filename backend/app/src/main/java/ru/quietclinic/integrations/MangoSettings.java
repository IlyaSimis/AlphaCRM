package ru.quietclinic.integrations;

/** Не секретные параметры Mango. */
record MangoSettings(String apiUrl, String defaultExtension, String lineNumber) {}
