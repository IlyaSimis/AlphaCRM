package ru.quietclinic.platform;

interface KmsPort {
  byte[] dataKey();

  String keyVersion();
}
