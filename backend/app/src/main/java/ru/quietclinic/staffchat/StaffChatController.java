package ru.quietclinic.staffchat;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.quietclinic.staffchat.StaffChatViews.ColleagueView;
import ru.quietclinic.staffchat.StaffChatViews.ConversationView;
import ru.quietclinic.staffchat.StaffChatViews.MessageView;
import ru.quietclinic.staffchat.StaffChatViews.PreferencesView;

/**
 * REST остаётся источником истины: после разрыва WebSocket интерфейс заново загружает эти данные.
 */
@RestController
@RequestMapping("/api/v1/staff-chat")
public class StaffChatController {
  private final StaffChatService chat;
  private final StaffChatHub hub;

  StaffChatController(StaffChatService chat, StaffChatHub hub) {
    this.chat = chat;
    this.hub = hub;
  }

  @GetMapping("/colleagues")
  List<ColleagueView> colleagues() {
    return chat.colleagues();
  }

  @GetMapping("/preferences")
  PreferencesView preferences() {
    return chat.preferences();
  }

  @PutMapping("/preferences")
  PreferencesView preferences(@Valid @RequestBody StaffChatPreferencesRequest request) {
    return chat.updatePreferences(request);
  }

  @GetMapping("/conversations")
  List<ConversationView> conversations() {
    return chat.conversations();
  }

  @PostMapping("/conversations/direct")
  ConversationView direct(@Valid @RequestBody CreateDirectConversationRequest request) {
    return chat.createDirect(request);
  }

  @PostMapping("/conversations/group")
  @ResponseStatus(HttpStatus.CREATED)
  ConversationView group(@Valid @RequestBody CreateGroupConversationRequest request) {
    return chat.createGroup(request);
  }

  @GetMapping("/conversations/{id}/messages")
  List<MessageView> messages(@PathVariable UUID id) {
    return chat.messages(id);
  }

  @PostMapping("/conversations/{id}/messages")
  @ResponseStatus(HttpStatus.CREATED)
  MessageView send(@PathVariable UUID id, @Valid @RequestBody SendStaffMessageRequest request) {
    MessageView message = chat.send(id, request);
    // Транзакция сервиса уже завершена: получатель никогда не увидит событие откаченного сообщения.
    hub.messageCreated(message);
    return message;
  }

  @PostMapping("/conversations/{id}/read")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void read(@PathVariable UUID id, @Valid @RequestBody MarkConversationReadRequest request) {
    chat.markRead(id, request.messageId());
  }
}
