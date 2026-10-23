package ru.quietclinic.registry;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Запись создана обычным POST /appointments; здесь ожидание закрывается ссылкой на неё. */
record BookWaitlistRequest(@NotNull UUID appointmentId) {}
