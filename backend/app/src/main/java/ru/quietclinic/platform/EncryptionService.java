package ru.quietclinic.platform;

/**
 * AES-GCM с ключом из {@link KmsPort}: случайный 12-байтовый nonce на каждое шифрование, версия
 * ключа хранится вместе с шифртекстом.
 */
public interface EncryptionService {
  String encrypt(String plaintext);

  String decrypt(String ciphertext);

  /**
   * Те же ключ и схема nonce для файлов. Результат: длина версии ключа (1 байт), версия ключа
   * (UTF-8), nonce, шифртекст с тегом GCM. Открытых байтов в результате нет.
   */
  byte[] encryptBytes(byte[] plaintext);

  byte[] decryptBytes(byte[] ciphertext);
}
