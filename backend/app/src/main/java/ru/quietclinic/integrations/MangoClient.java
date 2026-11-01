package ru.quietclinic.integrations;

/** Mango client, привязанный к одной клинике. */
public interface MangoClient {
  boolean configured();

  String defaultExtension();

  void callback(String commandId, String extension, String phone);
}
