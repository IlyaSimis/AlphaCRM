package ru.quietclinic.communications;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

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
