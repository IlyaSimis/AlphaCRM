package ru.quietclinic.integrations;

/** Зашифрованная версия секрета; открытые значения в этой записи отсутствуют. */
record IntegrationSecretRecord(byte[] ciphertext, byte[] wrappedDataKey, String keyVersion) {}
