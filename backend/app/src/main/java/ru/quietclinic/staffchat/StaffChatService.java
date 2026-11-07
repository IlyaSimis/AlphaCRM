package ru.quietclinic.staffchat;

import static ru.quietclinic.platform.PlatformConfig.badRequest;
import static ru.quietclinic.platform.PlatformConfig.clinicId;
import static ru.quietclinic.platform.PlatformConfig.conflict;
import static ru.quietclinic.platform.PlatformConfig.forbidden;
import static ru.quietclinic.platform.PlatformConfig.notFound;
import static ru.quietclinic.platform.PlatformConfig.staffId;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import ru.quietclinic.staffchat.StaffChatViews.AttachmentView;
import ru.quietclinic.staffchat.StaffChatViews.ColleagueView;
import ru.quietclinic.staffchat.StaffChatViews.ConversationView;
import ru.quietclinic.staffchat.StaffChatViews.MemberView;
import ru.quietclinic.staffchat.StaffChatViews.MessageView;
import ru.quietclinic.staffchat.StaffChatViews.PreferencesView;
import ru.quietclinic.staffchat.StaffChatViews.ReactionView;

/** Внутренний чат: каждая операция ограничена клиникой и членством учётной записи сотрудника. */
@Service
class StaffChatService {
  static final int MAX_ATTACHMENTS = 5;
  static final long MAX_ATTACHMENT_BYTES = 10L * 1024 * 1024;
  private static final int MAX_CONVERSATIONS = 200;
  private static final int MAX_MESSAGES = 200;
  private static final String MESSAGE_FIELDS =
      "m.id, m.conversation_id, m.sender_id, s.full_name as sender_name, m.body,"
          + " m.created_at, m.edited_at, m.deleted_at";
  private static final Set<String> ALLOWED_REACTIONS = Set.of("👍", "❤️", "😂", "😮", "😢", "🙏");

  private final JdbcTemplate jdbc;
  private final StaffChatAttachmentStorage storage;

  StaffChatService(JdbcTemplate jdbc, StaffChatAttachmentStorage storage) {
    this.jdbc = jdbc;
    this.storage = storage;
  }

  @Transactional(readOnly = true)
  List<ColleagueView> colleagues() {
    return jdbc.query(
        "select id, full_name, role from staff_user where clinic_id = ? and active = true"
            + " and id <> ? order by full_name, id",
        (row, index) ->
            new ColleagueView(
                row.getObject("id", UUID.class), row.getString("full_name"), row.getString("role")),
        clinicId(),
        staffId());
  }

  @Transactional(readOnly = true)
  PreferencesView preferences() {
    return jdbc
        .query(
            "select notifications_enabled, sound_enabled, browser_enabled"
                + " from staff_chat_preferences where staff_id = ?",
            (row, index) ->
                new PreferencesView(
                    row.getBoolean("notifications_enabled"),
                    row.getBoolean("sound_enabled"),
                    row.getBoolean("browser_enabled")),
            staffId())
        .stream()
        .findFirst()
        .orElse(new PreferencesView(true, true, false));
  }

  @Transactional
  PreferencesView updatePreferences(StaffChatPreferencesRequest request) {
    UUID staff = staffId();
    Instant now = Instant.now();
    int updated =
        jdbc.update(
            "update staff_chat_preferences set notifications_enabled = ?, sound_enabled = ?,"
                + " browser_enabled = ?, updated_at = ? where staff_id = ?",
            request.notificationsEnabled(),
            request.soundEnabled(),
            request.browserEnabled(),
            Timestamp.from(now),
            staff);
    if (updated == 0) {
      jdbc.update(
          "insert into staff_chat_preferences(staff_id, notifications_enabled, sound_enabled,"
              + " browser_enabled, updated_at) values (?, ?, ?, ?, ?)",
          staff,
          request.notificationsEnabled(),
          request.soundEnabled(),
          request.browserEnabled(),
          Timestamp.from(now));
    }
    return new PreferencesView(
        request.notificationsEnabled(), request.soundEnabled(), request.browserEnabled());
  }

  @Transactional(readOnly = true)
  List<ConversationView> conversations() {
    UUID clinic = clinicId();
    UUID staff = staffId();
    return jdbc
        .query(
            "select id, kind, title, updated_at from staff_conversation c where clinic_id = ?"
                + " and exists (select 1 from staff_conversation_member m"
                + " where m.conversation_id = c.id and m.staff_id = ?)"
                + " order by updated_at desc, id limit "
                + MAX_CONVERSATIONS,
            (row, index) -> conversation(row, clinic, staff),
            clinic,
            staff)
        .stream()
        .toList();
  }

  @Transactional(readOnly = true)
  ConversationView conversation(UUID id) {
    UUID clinic = clinicId();
    UUID staff = staffId();
    List<ConversationView> found =
        jdbc.query(
            "select id, kind, title, updated_at from staff_conversation c"
                + " where id = ? and clinic_id = ? and exists"
                + " (select 1 from staff_conversation_member m"
                + " where m.conversation_id = c.id and m.staff_id = ?)",
            (row, index) -> conversation(row, clinic, staff),
            id,
            clinic,
            staff);
    if (found.isEmpty()) throw notFound("CHAT_NOT_FOUND", "Conversation not found");
    return found.get(0);
  }

  @Transactional
  ConversationView createDirect(CreateDirectConversationRequest request) {
    UUID clinic = clinicId();
    UUID creator = staffId();
    UUID other = request.staffId();
    if (creator.equals(other)) {
      throw conflict("CHAT_DIRECT_SELF", "Choose another staff member");
    }
    requireActiveColleague(clinic, other);
    UUID first = creator.compareTo(other) < 0 ? creator : other;
    UUID second = creator.compareTo(other) < 0 ? other : creator;
    String directKey = first + ":" + second;
    Optional<UUID> existing = findDirect(clinic, directKey, creator);
    if (existing.isPresent()) return conversation(existing.get());

    UUID id = UUID.randomUUID();
    Instant now = Instant.now();
    jdbc.update(
        "insert into staff_conversation(id, clinic_id, kind, direct_key, created_by, created_at,"
            + " updated_at) values (?, ?, 'DIRECT', ?, ?, ?, ?)",
        id,
        clinic,
        directKey,
        creator,
        Timestamp.from(now),
        Timestamp.from(now));
    addMember(id, creator, now);
    addMember(id, other, now);
    return conversation(id);
  }

  @Transactional
  ConversationView createGroup(CreateGroupConversationRequest request) {
    UUID clinic = clinicId();
    UUID creator = staffId();
    Set<UUID> selected = new LinkedHashSet<>(request.memberIds());
    selected.remove(creator);
    if (selected.contains(null)) throw badRequest("VALIDATION_ERROR", "Member ID is required");
    if (selected.size() < 2) {
      throw conflict("CHAT_GROUP_MEMBERS_REQUIRED", "Choose at least two other staff members");
    }
    selected.forEach(member -> requireActiveColleague(clinic, member));

    UUID id = UUID.randomUUID();
    Instant now = Instant.now();
    jdbc.update(
        "insert into staff_conversation(id, clinic_id, kind, title, created_by, created_at,"
            + " updated_at) values (?, ?, 'GROUP', ?, ?, ?, ?)",
        id,
        clinic,
        request.title().trim(),
        creator,
        Timestamp.from(now),
        Timestamp.from(now));
    addMember(id, creator, now);
    selected.forEach(member -> addMember(id, member, now));
    return conversation(id);
  }

  @Transactional(readOnly = true)
  List<MessageView> messages(UUID conversationId, Long beforeId) {
    requireMembership(conversationId, clinicId(), staffId());
    List<MessageView> result =
        new ArrayList<>(loadMessages(conversationId, staffId(), beforeId, MAX_MESSAGES));
    Collections.reverse(result);
    return result;
  }

  @Transactional
  MessageView send(UUID conversationId, SendStaffMessageRequest request) {
    try {
      return send(conversationId, request, List.of());
    } catch (IOException ex) {
      throw new IllegalStateException("Text-only message does not read attachments", ex);
    }
  }

  @Transactional
  MessageView send(
      UUID conversationId, SendStaffMessageRequest request, List<MultipartFile> attachments)
      throws IOException {
    UUID clinic = clinicId();
    UUID sender = staffId();
    requireMembership(conversationId, clinic, sender);
    List<MultipartFile> files = attachments == null ? List.of() : attachments;
    validateAttachments(files);
    String body = request.text() == null ? "" : request.text().trim();
    if (body.length() > 4000) {
      throw badRequest("VALIDATION_ERROR", "Message text must not exceed 4000 characters");
    }
    if (body.isEmpty() && files.isEmpty()) {
      throw badRequest("VALIDATION_ERROR", "Message text or attachment is required");
    }
    Set<UUID> mentions =
        request.mentionIds() == null
            ? new LinkedHashSet<>()
            : new LinkedHashSet<>(request.mentionIds());
    mentions.remove(sender);
    if (mentions.contains(null)) throw badRequest("VALIDATION_ERROR", "Mention ID is required");
    mentions.forEach(member -> requireActiveMembership(conversationId, clinic, member));
    Long id = jdbc.queryForObject("select nextval('staff_chat_message_seq')", Long.class);
    Instant now = Instant.now();
    jdbc.update(
        "insert into staff_chat_message(id, conversation_id, clinic_id, sender_id, body, created_at)"
            + " values (?, ?, ?, ?, ?, ?)",
        id,
        conversationId,
        clinic,
        sender,
        body,
        Timestamp.from(now));
    mentions.forEach(
        member ->
            jdbc.update(
                "insert into staff_chat_message_mention(message_id, staff_id) values (?, ?)",
                id,
                member));
    for (MultipartFile file : files) {
      UUID attachmentId = UUID.randomUUID();
      String storageKey = UUID.randomUUID() + "/" + UUID.randomUUID();
      storage.put(storageKey, file.getBytes());
      jdbc.update(
          "insert into staff_chat_attachment(id, message_id, original_name, content_type,"
              + " size_bytes, storage_key) values (?, ?, ?, ?, ?, ?)",
          attachmentId,
          id,
          fileName(file.getOriginalFilename()),
          contentType(file.getContentType()),
          file.getSize(),
          storageKey);
    }
    jdbc.update(
        "update staff_conversation set updated_at = ? where id = ? and clinic_id = ?",
        Timestamp.from(now),
        conversationId,
        clinic);
    jdbc.update(
        "update staff_chat_read_state set last_read_message_id = ?, updated_at = ?"
            + " where conversation_id = ? and staff_id = ?",
        id,
        Timestamp.from(now),
        conversationId,
        sender);
    return message(id, conversationId, sender).orElseThrow();
  }

  @Transactional
  MessageView edit(long messageId, EditStaffMessageRequest request) {
    UUID staff = staffId();
    MessageAccess access = requireMessageAccess(messageId, staff, clinicId());
    requireAuthor(access, staff);
    requireNotDeleted(access);
    jdbc.update(
        "update staff_chat_message set body = ?, edited_at = ? where id = ?",
        request.text().trim(),
        Timestamp.from(Instant.now()),
        messageId);
    return message(messageId, access.conversationId(), staff).orElseThrow();
  }

  @Transactional
  MessageView delete(long messageId) {
    UUID staff = staffId();
    MessageAccess access = requireMessageAccess(messageId, staff, clinicId());
    requireAuthor(access, staff);
    requireNotDeleted(access);
    jdbc.update(
        "update staff_chat_message set body = '', deleted_at = ?, edited_at = null where id = ?",
        Timestamp.from(Instant.now()),
        messageId);
    jdbc.update("delete from staff_chat_reaction where message_id = ?", messageId);
    return message(messageId, access.conversationId(), staff).orElseThrow();
  }

  @Transactional
  MessageView react(long messageId, StaffChatReactionRequest request) {
    UUID staff = staffId();
    MessageAccess access = requireMessageAccess(messageId, staff, clinicId());
    requireNotDeleted(access);
    String emoji = request.emoji();
    if (!ALLOWED_REACTIONS.contains(emoji)) {
      throw badRequest("CHAT_REACTION_NOT_SUPPORTED", "Reaction is not supported");
    }
    List<String> current =
        jdbc.query(
            "select emoji from staff_chat_reaction where message_id = ? and staff_id = ?",
            (row, index) -> row.getString(1),
            messageId,
            staff);
    if (current.contains(emoji)) {
      jdbc.update(
          "delete from staff_chat_reaction where message_id = ? and staff_id = ?",
          messageId,
          staff);
    } else {
      int updated =
          jdbc.update(
              "update staff_chat_reaction set emoji = ?, created_at = ?"
                  + " where message_id = ? and staff_id = ?",
              emoji,
              Timestamp.from(Instant.now()),
              messageId,
              staff);
      if (updated == 0) {
        jdbc.update(
            "insert into staff_chat_reaction(message_id, staff_id, emoji, created_at)"
                + " values (?, ?, ?, ?)",
            messageId,
            staff,
            emoji,
            Timestamp.from(Instant.now()));
      }
    }
    return message(messageId, access.conversationId(), staff).orElseThrow();
  }

  @Transactional(readOnly = true)
  StaffChatAttachmentContent attachment(UUID attachmentId) {
    List<StaffChatAttachmentContent> found =
        jdbc.query(
            "select a.original_name, a.storage_key from staff_chat_attachment a"
                + " join staff_chat_message m on m.id = a.message_id"
                + " join staff_conversation_member member on member.conversation_id = m.conversation_id"
                + " where a.id = ? and m.clinic_id = ? and member.staff_id = ? and m.deleted_at is null",
            (row, index) ->
                new StaffChatAttachmentContent(
                    row.getString("original_name"), storage.get(row.getString("storage_key"))),
            attachmentId,
            clinicId(),
            staffId());
    if (found.isEmpty()) throw notFound("CHAT_ATTACHMENT_NOT_FOUND", "Attachment not found");
    return found.get(0);
  }

  @Transactional
  MessageView markRead(UUID conversationId, long messageId) {
    UUID staff = staffId();
    requireMembership(conversationId, clinicId(), staff);
    Long count =
        jdbc.queryForObject(
            "select count(*) from staff_chat_message where id = ? and conversation_id = ?",
            Long.class,
            messageId,
            conversationId);
    if (count == null || count == 0) {
      throw notFound("CHAT_MESSAGE_NOT_FOUND", "Message not found in this conversation");
    }
    jdbc.update(
        "update staff_chat_read_state set last_read_message_id ="
            + " greatest(coalesce(last_read_message_id, 0), ?), updated_at = ?"
            + " where conversation_id = ? and staff_id = ?",
        messageId,
        Timestamp.from(Instant.now()),
        conversationId,
        staff);
    return message(messageId, conversationId, staff).orElseThrow();
  }

  /**
   * Для WebSocket каждый получатель получает собственный флаг own и только при активном членстве.
   */
  @Transactional(readOnly = true)
  Optional<MessageView> messageForRecipient(long messageId, UUID staff, UUID clinic) {
    return jdbc
        .query(
            "select "
                + MESSAGE_FIELDS
                + " from staff_chat_message m join staff_user s on s.id = m.sender_id"
                + " join staff_conversation c on c.id = m.conversation_id"
                + " join staff_conversation_member member on member.conversation_id = c.id"
                + " join staff_user recipient on recipient.id = member.staff_id"
                + " where m.id = ? and c.clinic_id = ? and member.staff_id = ?"
                + " and recipient.active = true",
            (row, index) -> message(row, staff),
            messageId,
            clinic,
            staff)
        .stream()
        .findFirst();
  }

  private ConversationView conversation(ResultSet row, UUID clinic, UUID staff)
      throws SQLException {
    UUID id = row.getObject("id", UUID.class);
    String kind = row.getString("kind");
    List<MemberView> members = members(id);
    String title =
        "DIRECT".equals(kind)
            ? members.stream()
                .filter(member -> !member.id().equals(staff))
                .map(MemberView::fullName)
                .findFirst()
                .orElse("Личный чат")
            : row.getString("title");
    MessageView last =
        jdbc
            .query(
                "select "
                    + MESSAGE_FIELDS
                    + " from staff_chat_message m join staff_user s on s.id = m.sender_id"
                    + " where m.conversation_id = ? order by m.id desc limit 1",
                (messageRow, index) -> message(messageRow, staff),
                id)
            .stream()
            .findFirst()
            .orElse(null);
    Long unread =
        jdbc.queryForObject(
            "select count(*) from staff_chat_message m left join staff_chat_read_state r"
                + " on r.conversation_id = m.conversation_id and r.staff_id = ?"
                + " where m.conversation_id = ? and m.sender_id <> ?"
                + " and m.id > coalesce(r.last_read_message_id, 0)",
            Long.class,
            staff,
            id,
            staff);
    return new ConversationView(
        id,
        kind,
        title,
        members,
        last,
        unread == null ? 0 : unread,
        row.getTimestamp("updated_at").toInstant());
  }

  private List<MemberView> members(UUID conversationId) {
    return jdbc.query(
        "select s.id, s.full_name, s.role, s.active from staff_conversation_member m"
            + " join staff_user s on s.id = m.staff_id where m.conversation_id = ?"
            + " order by s.full_name, s.id",
        (row, index) ->
            new MemberView(
                row.getObject("id", UUID.class),
                row.getString("full_name"),
                row.getString("role"),
                row.getBoolean("active")),
        conversationId);
  }

  private List<MessageView> loadMessages(
      UUID conversationId, UUID staff, Long beforeId, int limit) {
    if (beforeId == null) {
      return jdbc.query(
          "select "
              + MESSAGE_FIELDS
              + " from staff_chat_message m join staff_user s on s.id = m.sender_id"
              + " where m.conversation_id = ? order by m.id desc limit "
              + limit,
          (row, index) -> message(row, staff),
          conversationId);
    }
    return jdbc.query(
        "select "
            + MESSAGE_FIELDS
            + " from staff_chat_message m join staff_user s on s.id = m.sender_id"
            + " where m.conversation_id = ? and m.id < ? order by m.id desc limit "
            + limit,
        (row, index) -> message(row, staff),
        conversationId,
        beforeId);
  }

  private Optional<MessageView> message(long id, UUID conversationId, UUID staff) {
    return jdbc
        .query(
            "select "
                + MESSAGE_FIELDS
                + " from staff_chat_message m join staff_user s on s.id = m.sender_id"
                + " where m.id = ? and m.conversation_id = ?",
            (row, index) -> message(row, staff),
            id,
            conversationId)
        .stream()
        .findFirst();
  }

  private MessageView message(ResultSet row, UUID viewer) throws SQLException {
    long id = row.getLong("id");
    UUID sender = row.getObject("sender_id", UUID.class);
    List<UUID> mentions = mentionIds(id);
    boolean deleted = row.getTimestamp("deleted_at") != null;
    Timestamp edited = row.getTimestamp("edited_at");
    return new MessageView(
        id,
        row.getObject("conversation_id", UUID.class),
        sender,
        row.getString("sender_name"),
        deleted ? "" : row.getString("body"),
        row.getTimestamp("created_at").toInstant(),
        mentions,
        sender.equals(viewer),
        mentions.contains(viewer),
        readByAll(id, row.getObject("conversation_id", UUID.class), sender),
        edited == null ? null : edited.toInstant(),
        deleted,
        deleted ? List.of() : attachments(id),
        deleted ? List.of() : reactions(id, viewer));
  }

  private boolean readByAll(long messageId, UUID conversationId, UUID sender) {
    Long unread =
        jdbc.queryForObject(
            "select count(*) from staff_conversation_member m"
                + " join staff_user s on s.id = m.staff_id"
                + " left join staff_chat_read_state r on r.conversation_id = m.conversation_id"
                + " and r.staff_id = m.staff_id"
                + " where m.conversation_id = ? and m.staff_id <> ? and s.active = true"
                + " and coalesce(r.last_read_message_id, 0) < ?",
            Long.class,
            conversationId,
            sender,
            messageId);
    return unread != null && unread == 0;
  }

  private List<AttachmentView> attachments(long messageId) {
    return jdbc.query(
        "select id, original_name, content_type, size_bytes from staff_chat_attachment"
            + " where message_id = ? order by id",
        (row, index) -> {
          UUID id = row.getObject("id", UUID.class);
          return new AttachmentView(
              id,
              row.getString("original_name"),
              row.getString("content_type"),
              row.getLong("size_bytes"),
              "/api/v1/staff-chat/attachments/" + id);
        },
        messageId);
  }

  private List<ReactionView> reactions(long messageId, UUID viewer) {
    Map<String, List<UUID>> grouped = new LinkedHashMap<>();
    jdbc.query(
        "select emoji, staff_id from staff_chat_reaction where message_id = ?"
            + " order by emoji, staff_id",
        row -> {
          grouped
              .computeIfAbsent(row.getString("emoji"), key -> new ArrayList<>())
              .add(row.getObject("staff_id", UUID.class));
        },
        messageId);
    return grouped.entrySet().stream()
        .map(
            entry ->
                new ReactionView(
                    entry.getKey(), entry.getValue().size(), entry.getValue().contains(viewer)))
        .toList();
  }

  private List<UUID> mentionIds(long messageId) {
    return jdbc.query(
        "select staff_id from staff_chat_message_mention where message_id = ? order by staff_id",
        (row, index) -> row.getObject("staff_id", UUID.class),
        messageId);
  }

  private Optional<UUID> findDirect(UUID clinic, String directKey, UUID staff) {
    return jdbc
        .query(
            "select c.id from staff_conversation c where c.clinic_id = ? and c.direct_key = ?"
                + " and exists (select 1 from staff_conversation_member m"
                + " where m.conversation_id = c.id and m.staff_id = ?)",
            (row, index) -> row.getObject("id", UUID.class),
            clinic,
            directKey,
            staff)
        .stream()
        .findFirst();
  }

  private void validateAttachments(List<MultipartFile> files) {
    if (files.size() > MAX_ATTACHMENTS) {
      throw badRequest("CHAT_TOO_MANY_ATTACHMENTS", "At most 5 files per message are allowed");
    }
    for (MultipartFile file : files) {
      if (file.isEmpty() || file.getSize() > MAX_ATTACHMENT_BYTES) {
        throw badRequest("CHAT_ATTACHMENT_TOO_LARGE", "Each file must be between 1 byte and 10 MB");
      }
    }
  }

  private String fileName(String original) {
    String safe = original == null ? "file" : original.replaceAll("[\\r\\n\\\\/]", "_").trim();
    if (safe.isEmpty()) return "file";
    return safe.length() > 255 ? safe.substring(safe.length() - 255) : safe;
  }

  private String contentType(String original) {
    if (original == null || original.isBlank()) return "application/octet-stream";
    return original.length() > 255 ? original.substring(0, 255) : original;
  }

  private MessageAccess requireMessageAccess(long messageId, UUID staff, UUID clinic) {
    List<MessageAccess> found =
        jdbc.query(
            "select m.conversation_id, m.sender_id, m.deleted_at from staff_chat_message m"
                + " join staff_conversation_member member on member.conversation_id = m.conversation_id"
                + " where m.id = ? and m.clinic_id = ? and member.staff_id = ?",
            (row, index) ->
                new MessageAccess(
                    row.getObject("conversation_id", UUID.class),
                    row.getObject("sender_id", UUID.class),
                    row.getTimestamp("deleted_at") != null),
            messageId,
            clinic,
            staff);
    if (found.isEmpty()) throw notFound("CHAT_MESSAGE_NOT_FOUND", "Message not found");
    return found.get(0);
  }

  private void requireAuthor(MessageAccess access, UUID staff) {
    if (!access.senderId().equals(staff)) {
      throw forbidden("CHAT_NOT_AUTHOR", "Only the author can change this message");
    }
  }

  private void requireNotDeleted(MessageAccess access) {
    if (access.deleted()) throw conflict("CHAT_MESSAGE_DELETED", "Message is already deleted");
  }

  private void requireActiveColleague(UUID clinic, UUID staff) {
    Long count =
        jdbc.queryForObject(
            "select count(*) from staff_user where id = ? and clinic_id = ? and active = true",
            Long.class,
            staff,
            clinic);
    if (count == null || count == 0) {
      throw notFound("STAFF_NOT_FOUND", "Active staff member not found");
    }
  }

  private void requireMembership(UUID conversationId, UUID clinic, UUID staff) {
    Long count =
        jdbc.queryForObject(
            "select count(*) from staff_conversation c join staff_conversation_member m"
                + " on m.conversation_id = c.id where c.id = ? and c.clinic_id = ? and m.staff_id = ?",
            Long.class,
            conversationId,
            clinic,
            staff);
    if (count == null || count == 0) {
      throw notFound("CHAT_NOT_FOUND", "Conversation not found");
    }
  }

  private void requireActiveMembership(UUID conversationId, UUID clinic, UUID staff) {
    Long count =
        jdbc.queryForObject(
            "select count(*) from staff_conversation c join staff_conversation_member m"
                + " on m.conversation_id = c.id join staff_user s on s.id = m.staff_id"
                + " where c.id = ? and c.clinic_id = ? and m.staff_id = ? and s.active = true",
            Long.class,
            conversationId,
            clinic,
            staff);
    if (count == null || count == 0) {
      throw badRequest("CHAT_MENTION_NOT_MEMBER", "Mentioned staff member is not in conversation");
    }
  }

  private void addMember(UUID conversationId, UUID staff, Instant joinedAt) {
    jdbc.update(
        "insert into staff_conversation_member(conversation_id, staff_id, joined_at) values (?, ?, ?)",
        conversationId,
        staff,
        Timestamp.from(joinedAt));
    jdbc.update(
        "insert into staff_chat_read_state(conversation_id, staff_id, updated_at) values (?, ?, ?)",
        conversationId,
        staff,
        Timestamp.from(joinedAt));
  }

  private record MessageAccess(UUID conversationId, UUID senderId, boolean deleted) {}
}
