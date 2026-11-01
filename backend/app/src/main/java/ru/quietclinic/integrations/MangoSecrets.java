package ru.quietclinic.integrations;

/** Секреты существуют только в запросе записи и в памяти scoped client. */
record MangoSecrets(String apiKey, String apiSalt) {}
