package ru.quietclinic.platform;

interface KmsPort {
  byte[] dataKey();

  /** Ключ конкретной версии: старые записи остаются читаемыми после ротации. */
  byte[] dataKey(String version);

  /** Стабильный отдельный ключ только для HMAC-индексов, не для шифрования содержимого. */
  byte[] indexKey();

  String keyVersion();
}
