package ru.quietclinic.platform;

import java.util.Set;

/**
 * Потребитель доменных событий. Обработка идёт в своей транзакции вместе с отметкой в inbox по
 * {@code (consumer, eventId)}: повтор доставки того же события ничего не меняет. Во время обработки
 * {@link PlatformConfig#clinicId()} и {@link PlatformConfig#actorId()} — клиника и автор события;
 * разрешений у обработчика нет, поэтому он вызывает только внутренние методы сервисов.
 */
public interface DomainEventHandler {
  /** Уникальное имя потребителя, например {@code billing.charge-on-completed}. */
  String consumer();

  /** Типы событий, которые обрабатывает потребитель. */
  Set<String> types();

  void handle(DomainEvent event);
}
