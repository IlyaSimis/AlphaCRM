package ru.quietclinic.tenancy;

/** Созданное подключение; пароль сразу шифруется каталогом и наружу не возвращается. */
record TenantDatabase(String url, String username, String password) {}
