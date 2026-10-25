package ru.quietclinic.communications;

import java.util.ArrayList;
import java.util.List;

/** Поддельный канал: запоминает сообщения и отвечает «доставлено». */
final class RecordingPort implements NotificationPort {
  static final String CHANNEL = "TEST";
  final List<PatientMessage> sent = new ArrayList<>();

  @Override
  public String channelFor(PatientMessage message) {
    return CHANNEL;
  }

  @Override
  public DeliveryResult deliver(PatientMessage message) {
    sent.add(message);
    return DeliveryResult.sent(CHANNEL);
  }

  List<PatientMessage> sentFor(String appointment, String template) {
    return sent.stream()
        .filter(m -> m.appointmentId().toString().equals(appointment))
        .filter(m -> template == null || m.template().equals(template))
        .toList();
  }
}
