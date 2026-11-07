package ru.quietclinic.staffchat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Публичные представления чата: ни username, ни другие учётные данные здесь не нужны. */
final class StaffChatViews {
  private StaffChatViews() {}

  record ColleagueView(UUID id, String fullName, String role) {}

  record MemberView(UUID id, String fullName, String role, boolean active) {}

  record PreferencesView(
      boolean notificationsEnabled, boolean soundEnabled, boolean browserEnabled) {}

  record AttachmentView(
      UUID id, String name, String contentType, long sizeBytes, String downloadUrl) {}

  record ReactionView(String emoji, long count, boolean own) {}

  record MessageView(
      long id,
      UUID conversationId,
      UUID senderId,
      String senderName,
      String body,
      Instant createdAt,
      List<UUID> mentionIds,
      boolean own,
      boolean mentioned,
      boolean readByAll,
      Instant editedAt,
      boolean deleted,
      List<AttachmentView> attachments,
      List<ReactionView> reactions) {}

  record ConversationView(
      UUID id,
      String kind,
      String title,
      List<MemberView> members,
      MessageView lastMessage,
      long unreadCount,
      Instant updatedAt) {}

  record MessageEvent(String type, MessageView message) {
    static MessageEvent created(MessageView message) {
      return new MessageEvent("MESSAGE_CREATED", message);
    }

    static MessageEvent updated(MessageView message) {
      return new MessageEvent("MESSAGE_UPDATED", message);
    }
  }
}
