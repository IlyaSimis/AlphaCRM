package ru.quietclinic.staffchat;

import java.io.IOException;
import java.security.Principal;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import ru.quietclinic.platform.ClinicMember;
import ru.quietclinic.staffchat.StaffChatViews.MessageEvent;
import ru.quietclinic.staffchat.StaffChatViews.MessageView;
import tools.jackson.databind.json.JsonMapper;

/** Локальная доставка активным вкладкам. Для нескольких backend-инстансов понадобится брокер. */
@Component
class StaffChatHub extends TextWebSocketHandler {
  private static final String STAFF_ATTRIBUTE = "staffId";
  private static final String CLINIC_ATTRIBUTE = "clinicId";

  private final StaffChatService chat;
  private final JsonMapper json;
  private final Map<UUID, Set<WebSocketSession>> sessions = new ConcurrentHashMap<>();

  StaffChatHub(StaffChatService chat, JsonMapper json) {
    this.chat = chat;
    this.json = json;
  }

  @Override
  public void afterConnectionEstablished(WebSocketSession session) throws IOException {
    ClinicMember member = member(session.getPrincipal());
    if (member == null || member.staffId() == null || member.clinicId() == null) {
      session.close(CloseStatus.POLICY_VIOLATION);
      return;
    }
    session.getAttributes().put(STAFF_ATTRIBUTE, member.staffId());
    session.getAttributes().put(CLINIC_ATTRIBUTE, member.clinicId());
    sessions.computeIfAbsent(member.staffId(), key -> new CopyOnWriteArraySet<>()).add(session);
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
    remove(session);
  }

  @Override
  public void handleTransportError(WebSocketSession session, Throwable exception)
      throws IOException {
    remove(session);
    if (session.isOpen()) session.close(CloseStatus.SERVER_ERROR);
  }

  void messageCreated(MessageView message) {
    publish(message, false);
  }

  void messageUpdated(MessageView message) {
    publish(message, true);
  }

  private void publish(MessageView message, boolean updated) {
    for (Map.Entry<UUID, Set<WebSocketSession>> entry : sessions.entrySet()) {
      for (WebSocketSession session : entry.getValue()) {
        UUID clinic = (UUID) session.getAttributes().get(CLINIC_ATTRIBUTE);
        chat.messageForRecipient(message.id(), entry.getKey(), clinic)
            .ifPresent(
                recipientMessage ->
                    send(
                        session,
                        updated
                            ? MessageEvent.updated(recipientMessage)
                            : MessageEvent.created(recipientMessage)));
      }
    }
  }

  private void send(WebSocketSession session, MessageEvent event) {
    if (!session.isOpen()) {
      remove(session);
      return;
    }
    try {
      // Spring WebSocket не разрешает параллельную запись в одно соединение.
      synchronized (session) {
        session.sendMessage(new TextMessage(json.writeValueAsString(event)));
      }
    } catch (IOException | RuntimeException ex) {
      remove(session);
      try {
        session.close(CloseStatus.SERVER_ERROR);
      } catch (IOException ignored) {
        // Соединение уже разорвано; REST-догрузка восстановит состояние после входа.
      }
    }
  }

  private void remove(WebSocketSession session) {
    Object staff = session.getAttributes().get(STAFF_ATTRIBUTE);
    if (!(staff instanceof UUID staffId)) return;
    sessions.computeIfPresent(
        staffId,
        (key, open) -> {
          open.remove(session);
          return open.isEmpty() ? null : open;
        });
  }

  private static ClinicMember member(Principal principal) {
    if (principal instanceof Authentication authentication
        && authentication.getPrincipal() instanceof ClinicMember member) {
      return member;
    }
    return principal instanceof ClinicMember member ? member : null;
  }
}
