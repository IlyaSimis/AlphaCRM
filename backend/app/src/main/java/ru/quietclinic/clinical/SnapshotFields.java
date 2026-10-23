package ru.quietclinic.clinical;

/** Четыре поля подписанного снимка документа. null — поля нет в снимке. */
record SnapshotFields(
    String complaints, String mentalStatus, String diagnosis, String recommendations) {

  static final SnapshotFields EMPTY = new SnapshotFields(null, null, null, null);
}
