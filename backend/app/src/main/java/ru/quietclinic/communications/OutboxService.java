package ru.quietclinic.communications;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboxService {
  private final OutboxRepository repository;

  OutboxService(OutboxRepository repository) {
    this.repository = repository;
  }

  @Transactional
  public void enqueue(String topic, String aggregateType, UUID aggregateId, String safePayload) {
    repository.save(new OutboxMessage(topic, aggregateType, aggregateId, safePayload));
  }
}
