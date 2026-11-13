package ru.quietclinic.staffchat;

import jakarta.validation.Valid;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
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
  List<MessageView> messages(@PathVariable UUID id, @RequestParam(required = false) Long beforeId) {
    return chat.messages(id, beforeId);
  }

  @PostMapping("/conversations/{id}/messages")
  @ResponseStatus(HttpStatus.CREATED)
  MessageView send(@PathVariable UUID id, @Valid @RequestBody SendStaffMessageRequest request) {
    MessageView message = chat.send(id, request);
    hub.messageCreated(message);
    return message;
  }

  @PostMapping(
      path = "/conversations/{id}/messages",
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  MessageView sendWithAttachments(
      @PathVariable UUID id,
      @RequestParam(required = false) String text,
      @RequestParam(required = false) List<UUID> mentionIds,
      @RequestParam("files") List<MultipartFile> files)
      throws IOException {
    MessageView message = chat.send(id, new SendStaffMessageRequest(text, mentionIds), files);
    hub.messageCreated(message);
    return message;
  }

  @PatchMapping("/messages/{messageId}")
  MessageView edit(
      @PathVariable long messageId, @Valid @RequestBody EditStaffMessageRequest request) {
    MessageView message = chat.edit(messageId, request);
    hub.messageUpdated(message);
    return message;
  }

  @DeleteMapping("/messages/{messageId}")
  MessageView delete(@PathVariable long messageId) {
    MessageView message = chat.delete(messageId);
    hub.messageUpdated(message);
    return message;
  }

  @PutMapping("/messages/{messageId}/reaction")
  MessageView react(
      @PathVariable long messageId, @Valid @RequestBody StaffChatReactionRequest request) {
    MessageView message = chat.react(messageId, request);
    hub.messageUpdated(message);
    return message;
  }

  @GetMapping("/attachments/{attachmentId}")
  ResponseEntity<byte[]> attachment(@PathVariable UUID attachmentId) {
    StaffChatAttachmentContent content = chat.attachment(attachmentId);
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_OCTET_STREAM)
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment()
                .filename(content.name(), StandardCharsets.UTF_8)
                .build()
                .toString())
        .header(HttpHeaders.CACHE_CONTROL, "no-store")
        .body(content.bytes());
  }

  @PostMapping("/conversations/{id}/read")
  MessageView read(@PathVariable UUID id, @Valid @RequestBody MarkConversationReadRequest request) {
    MessageView message = chat.markRead(id, request.messageId());
    hub.messageUpdated(message);
    return message;
  }
}
