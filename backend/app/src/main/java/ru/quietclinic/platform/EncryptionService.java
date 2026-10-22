package ru.quietclinic.platform;

public interface EncryptionService {
  String encrypt(String plaintext);

  String decrypt(String ciphertext);
}
