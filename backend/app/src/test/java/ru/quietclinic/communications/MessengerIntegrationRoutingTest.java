package ru.quietclinic.communications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import ru.quietclinic.integrations.MessengerIntegration;
import ru.quietclinic.integrations.MessengerIntegrationClientFactory;
import ru.quietclinic.integrations.MessengerSecrets;
import ru.quietclinic.integrations.MessengerSettings;

class MessengerIntegrationRoutingTest {
  private static final UUID CLINIC = UUID.randomUUID();
  private HttpTransport http;
  private MessengerIntegrationClientFactory integrations;
  private ConfiguredNotificationPort port;

  @BeforeEach
  void setUp() {
    http = Mockito.mock(HttpTransport.class);
    integrations = Mockito.mock(MessengerIntegrationClientFactory.class);
    var scoped = new MessengerChannelFactory(integrations, http, new NoticeText("UTC"));
    port =
        new ConfiguredNotificationPort(
            new WebhookChannel(http, "", ""),
            new TelegramChannel(http, "legacy-telegram", "https://api.telegram.org"),
            new MaxChannel(http, "", "https://platform-api2.max.ru", true),
            new SafeMockNotificationAdapter(),
            scoped);
  }

  @Test
  void sendsTelegramWithTheClinicTokenInsteadOfLegacy() throws Exception {
    when(integrations.forClinic(CLINIC, "TELEGRAM"))
        .thenReturn(Optional.of(configuration("TELEGRAM", "clinic-telegram", true)));
    when(http.postJson(any(), any(), any())).thenReturn(200);

    assertThat(port.channelFor(message("TELEGRAM"))).isEqualTo("TELEGRAM");
    assertThat(port.deliver(message("TELEGRAM")).outcome()).isEqualTo(DeliveryOutcome.SENT);
    var uri = ArgumentCaptor.forClass(URI.class);
    verify(http).postJson(uri.capture(), any(), any());
    assertThat(uri.getValue().toString()).contains("/botclinic-telegram/").doesNotContain("legacy");
  }

  @Test
  void sendsMaxWithTheClinicToken() throws Exception {
    when(integrations.forClinic(CLINIC, "MAX"))
        .thenReturn(Optional.of(configuration("MAX", "clinic-max", true)));
    when(http.postJson(any(), any(), any())).thenReturn(200);

    assertThat(port.deliver(message("MAX")).outcome()).isEqualTo(DeliveryOutcome.SENT);
    var headers = ArgumentCaptor.forClass(java.util.Map.class);
    verify(http).postJson(any(), any(), headers.capture());
    assertThat(headers.getValue()).containsEntry("Authorization", "clinic-max");
  }

  @Test
  void disabledClinicIntegrationNeverFallsBackToLegacy() throws Exception {
    when(integrations.forClinic(CLINIC, "TELEGRAM"))
        .thenReturn(Optional.of(configuration("TELEGRAM", "disabled-token", false)));

    assertThat(port.channelFor(message("TELEGRAM"))).isEqualTo("MOCK");
    assertThat(port.deliver(message("TELEGRAM")).outcome())
        .isEqualTo(DeliveryOutcome.NOT_CONFIGURED);
    verify(http, never()).postJson(any(), any(), any());
  }

  private static MessengerIntegration configuration(String type, String token, boolean active) {
    var apiUrl = type.equals("MAX") ? "https://platform-api2.max.ru" : "https://api.telegram.org";
    return new MessengerIntegration(
        new MessengerSettings(apiUrl, "", "", true), new MessengerSecrets(token, "", ""), active);
  }

  private static PatientMessage message(String preferred) {
    return new PatientMessage(
        "appointment-confirmed:x:1",
        "appointment-confirmed",
        UUID.randomUUID(),
        UUID.randomUUID(),
        "Clinic",
        "Visit",
        "Doctor",
        Instant.parse("2027-02-02T09:00:00Z"),
        "2027-02-02",
        "09:00",
        "Booked",
        "12345",
        "max-user",
        preferred,
        CLINIC);
  }
}
