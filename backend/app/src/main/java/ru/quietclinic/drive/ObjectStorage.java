package ru.quietclinic.drive;

/**
 * Хранилище объектов диска. Ключ — случайный ({@code clinicId/uuid}), имя файла в ключе не
 * встречается. Сейчас — каталог на диске ({@link LocalObjectStorage}, Docker-том); следующий шаг —
 * S3-совместимое хранилище (MinIO) с тем же контрактом.
 */
interface ObjectStorage {
  void put(String key, byte[] content);

  byte[] get(String key);

  void delete(String key);
}
