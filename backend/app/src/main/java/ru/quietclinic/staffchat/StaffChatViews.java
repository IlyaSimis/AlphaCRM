package ru.quietclinic.staffchat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Публичные представления чата: ни username, ни другие учётные данные здесь не нужны. */
final class StaffChatViews {
  private StaffChatViews() {}

  record ColleagueView(UUID id, String fullName, String role) {}

  record MemberView(UUID id, String fullName, String role, boolean active) {}

  record MessageView(
      long id,
      UUID conversationId,
      UUID senderId,
      String senderName,
      String body,
      Instant createdAt,
      boolean own) {}

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
  }
}
