package ru.quietclinic.clinical;

/** Расшифрованный файл для выгрузки: имя для Content-Disposition и байты. */
record FileDownload(String originalName, byte[] content) {}
