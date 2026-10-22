package ru.quietclinic.communications;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
class SafeMockNotificationAdapter implements NotificationPort {
  private static final Logger log = LoggerFactory.getLogger(SafeMockNotificationAdapter.class);

  public DeliveryResult sendNeutral(String destinationRef, String templateKey) {
    log.info("mock_notification template={} destinationRef=redacted", templateKey);
    return new DeliveryResult("mock-" + UUID.randomUUID(), "DELIVERED");
  }
}
