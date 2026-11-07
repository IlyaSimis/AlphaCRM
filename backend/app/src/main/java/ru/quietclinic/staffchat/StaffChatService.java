package ru.quietclinic.staffchat;

import static ru.quietclinic.platform.PlatformConfig.badRequest;
import static ru.quietclinic.platform.PlatformConfig.clinicId;
import static ru.quietclinic.platform.PlatformConfig.conflict;
import static ru.quietclinic.platform.PlatformConfig.notFound;
import static ru.quietclinic.platform.PlatformConfig.staffId;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.quietclinic.staffchat.StaffChatViews.ColleagueView;
import ru.quietclinic.staffchat.StaffChatViews.ConversationView;
import ru.quietclinic.staffchat.StaffChatViews.MemberView;
import ru.quietclinic.staffchat.StaffChatViews.MessageView;
import ru.quietclinic.staffchat.StaffChatViews.PreferencesView;

/** Внутренний чат: каждая операция ограничена клиникой и членством учётной записи сотрудника. */
@Service
class StaffChatService {
  private static final int MAX_CONVERSATIONS = 200;
  private static final int MAX_MESSAGES = 200;

  private final JdbcTemplate jdbc;

  StaffChatService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
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
  List<MessageView> messages(UUID conversationId) {
    requireMembership(conversationId, clinicId(), staffId());
    List<MessageView> result =
        new ArrayList<>(loadMessages(conversationId, staffId(), MAX_MESSAGES));
    Collections.reverse(result);
    return result;
  }

  @Transactional
  MessageView send(UUID conversationId, SendStaffMessageRequest request) {
    UUID clinic = clinicId();
    UUID sender = staffId();
    requireMembership(conversationId, clinic, sender);
    String body = request.text().trim();
    if (body.isEmpty()) throw badRequest("VALIDATION_ERROR", "Message text is required");
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
  void markRead(UUID conversationId, long messageId) {
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
  }

  /**
   * Для WebSocket каждый получатель получает собственный флаг own и только при активном членстве.
   */
  @Transactional(readOnly = true)
  Optional<MessageView> messageForRecipient(long messageId, UUID staff, UUID clinic) {
    return jdbc
        .query(
            "select m.id, m.conversation_id, m.sender_id, s.full_name as sender_name, m.body,"
                + " m.created_at from staff_chat_message m join staff_user s on s.id = m.sender_id"
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
                "select m.id, m.conversation_id, m.sender_id, s.full_name as sender_name, m.body,"
                    + " m.created_at from staff_chat_message m join staff_user s on s.id = m.sender_id"
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

  private List<MessageView> loadMessages(UUID conversationId, UUID staff, int limit) {
    return jdbc.query(
        "select m.id, m.conversation_id, m.sender_id, s.full_name as sender_name, m.body,"
            + " m.created_at from staff_chat_message m join staff_user s on s.id = m.sender_id"
            + " where m.conversation_id = ? order by m.id desc limit "
            + limit,
        (row, index) -> message(row, staff),
        conversationId);
  }

  private Optional<MessageView> message(long id, UUID conversationId, UUID staff) {
    return jdbc
        .query(
            "select m.id, m.conversation_id, m.sender_id, s.full_name as sender_name, m.body,"
                + " m.created_at from staff_chat_message m join staff_user s on s.id = m.sender_id"
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
    return new MessageView(
        id,
        row.getObject("conversation_id", UUID.class),
        sender,
        row.getString("sender_name"),
        row.getString("body"),
        row.getTimestamp("created_at").toInstant(),
        mentions,
        sender.equals(viewer),
        mentions.contains(viewer));
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
}
