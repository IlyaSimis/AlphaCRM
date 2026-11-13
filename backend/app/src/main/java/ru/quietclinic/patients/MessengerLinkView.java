package ru.quietclinic.patients;

import java.time.Instant;

/** Токен показывается один раз; в БД остаётся только его keyed-hash. */
public record MessengerLinkView(String channel, String token, String url, Instant expiresAt) {}
