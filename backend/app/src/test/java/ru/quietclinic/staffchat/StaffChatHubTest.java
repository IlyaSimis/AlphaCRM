package ru.quietclinic.staffchat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.Authentication;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import ru.quietclinic.platform.ClinicMember;
import ru.quietclinic.staffchat.StaffChatViews.MessageView;
import tools.jackson.databind.json.JsonMapper;

/** Событие уходит только активному участнику, с флагом own для конкретного получателя. */
class StaffChatHubTest {
  private final StaffChatService chat = mock(StaffChatService.class);
  private final StaffChatHub hub = new StaffChatHub(chat, JsonMapper.builder().build());

  @Test
  void deliversIndividualMessageViewToStaffSession() throws Exception {
    UUID clinic = UUID.randomUUID();
    UUID staff = UUID.randomUUID();
    UUID conversation = UUID.randomUUID();
    ClinicMember member = member(clinic, staff);
    Authentication authentication = mock(Authentication.class);
    when(authentication.getPrincipal()).thenReturn(member);
    WebSocketSession session = mock(WebSocketSession.class);
    when(session.getPrincipal()).thenReturn(authentication);
    when(session.getAttributes()).thenReturn(new HashMap<>());
    when(session.isOpen()).thenReturn(true);
    hub.afterConnectionEstablished(session);

    MessageView sent = message(10, conversation, staff, "Смена закрыта", true);
    MessageView received = message(10, conversation, UUID.randomUUID(), "Смена закрыта", false);
    when(chat.messageForRecipient(10, staff, clinic)).thenReturn(Optional.of(received));
    hub.messageCreated(sent);

    ArgumentCaptor<TextMessage> event = ArgumentCaptor.forClass(TextMessage.class);
    verify(session).sendMessage(event.capture());
    assertThat(event.getValue().getPayload())
        .contains("MESSAGE_CREATED")
        .contains("Смена закрыта")
        .contains("\"own\":false");
  }

  @Test
  void doesNotSendWhenRecipientIsNoLongerAnActiveMember() throws Exception {
    UUID clinic = UUID.randomUUID();
    UUID staff = UUID.randomUUID();
    Authentication authentication = mock(Authentication.class);
    when(authentication.getPrincipal()).thenReturn(member(clinic, staff));
    WebSocketSession session = mock(WebSocketSession.class);
    when(session.getPrincipal()).thenReturn(authentication);
    when(session.getAttributes()).thenReturn(new HashMap<>());
    when(session.isOpen()).thenReturn(true);
    hub.afterConnectionEstablished(session);
    when(chat.messageForRecipient(11, staff, clinic)).thenReturn(Optional.empty());

    hub.messageCreated(message(11, UUID.randomUUID(), UUID.randomUUID(), "Не доставлять", true));
    verify(session, never()).sendMessage(any());
  }

  private static ClinicMember member(UUID clinic, UUID staff) {
    return new ClinicMember() {
      @Override
      public UUID clinicId() {
        return clinic;
      }

      @Override
      public UUID staffId() {
        return staff;
      }
    };
  }

  private static MessageView message(
      long id, UUID conversation, UUID sender, String body, boolean own) {
    return new MessageView(
        id, conversation, sender, "Сотрудник", body, Instant.now(), List.of(), own, false);
  }
}
