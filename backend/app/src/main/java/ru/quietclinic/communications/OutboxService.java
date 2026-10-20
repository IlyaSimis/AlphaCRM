package ru.quietclinic.communications;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Entity
@Table(name = "outbox_message")
class OutboxMessage {
  @Id UUID id;

  @Column(nullable = false)
  String topic;

  @Column(nullable = false)
  String aggregateType;

  @Column(nullable = false)
  UUID aggregateId;

  @Column(nullable = false, length = 4000)
  String payload;

  @Column(nullable = false)
  String status;

  @Column(nullable = false)
  Instant createdAt;

  protected OutboxMessage() {}

  OutboxMessage(String topic, String aggregateType, UUID aggregateId, String payload) {
    id = UUID.randomUUID();
    this.topic = topic;
    this.aggregateType = aggregateType;
    this.aggregateId = aggregateId;
    this.payload = payload;
    status = "PENDING";
    createdAt = Instant.now();
  }
}

interface OutboxRepository extends JpaRepository<OutboxMessage, UUID> {}

interface NotificationPort {
  DeliveryResult sendNeutral(String destinationRef, String templateKey);
}

record DeliveryResult(String providerId, String status) {}

@Component
class SafeMockNotificationAdapter implements NotificationPort {
  private static final Logger log = LoggerFactory.getLogger(SafeMockNotificationAdapter.class);

  public DeliveryResult sendNeutral(String destinationRef, String templateKey) {
    log.info("mock_notification template={} destinationRef=redacted", templateKey);
    return new DeliveryResult("mock-" + UUID.randomUUID(), "DELIVERED");
  }
}

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
