package ru.quietclinic.drive;

import static ru.quietclinic.platform.PlatformConfig.clinicId;
import static ru.quietclinic.platform.PlatformConfig.conflict;
import static ru.quietclinic.platform.PlatformConfig.currentRoles;
import static ru.quietclinic.platform.PlatformConfig.forbidden;
import static ru.quietclinic.platform.PlatformConfig.notFound;
import static ru.quietclinic.platform.PlatformConfig.payloadTooLarge;
import static ru.quietclinic.platform.PlatformConfig.requirePermission;
import static ru.quietclinic.platform.PlatformConfig.staffId;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import ru.quietclinic.audit.AuditService;
import ru.quietclinic.contracts.LibreOfficePdfConverter;
import ru.quietclinic.platform.EncryptionService;
import ru.quietclinic.platform.Permission;
import ru.quietclinic.platform.RolePermissions;

/**
 * Корпоративный диск. Права: VIEW (смотреть, скачивать), EDIT (новая версия, переименование,
 * создание внутри папки), MANAGE (делиться, удалять в корзину, переносить). Владелец узла и
 * владелец любой папки выше — всегда MANAGE. Право, выданное на папку, наследуется содержимым;
 * явное право на вложенном узле может только расширить унаследованное. Эффективное право — максимум
 * из личных, ролевых, клинических и унаследованных. Ссылка (LINK) работает только после входа
 * сотрудника той же клиники и только через {@code /drive/links/{token}}.
 */
@Service
class DriveService {
  static final List<String> RANK = List.of("VIEW", "EDIT", "MANAGE");
  private static final SecureRandom RANDOM = new SecureRandom();

  private final DriveNodeRepository nodes;
  private final DriveVersionRepository versions;
  private final DriveShareRepository shares;
  private final DriveQuotaRepository quotas;
  private final ObjectStorage storage;
  private final EncryptionService encryption;
  private final JdbcTemplate jdbc;
  private final AuditService audit;
  private final LibreOfficePdfConverter pdf;
  private final long maxFileBytes;
  private final long defaultQuota;
  private final int keepVersions;
  private final int maxLinkDays;

  DriveService(
      DriveNodeRepository nodes,
      DriveVersionRepository versions,
      DriveShareRepository shares,
      DriveQuotaRepository quotas,
      ObjectStorage storage,
      EncryptionService encryption,
      JdbcTemplate jdbc,
      AuditService audit,
      LibreOfficePdfConverter pdf,
      @Value("${clinic.drive.max-file-bytes:104857600}") long maxFileBytes,
      @Value("${clinic.drive.default-quota-bytes:10737418240}") long defaultQuota,
      @Value("${clinic.drive.keep-versions:20}") int keepVersions,
      @Value("${clinic.drive.max-link-days:30}") int maxLinkDays) {
    this.nodes = nodes;
    this.versions = versions;
    this.shares = shares;
    this.quotas = quotas;
    this.storage = storage;
    this.encryption = encryption;
    this.jdbc = jdbc;
    this.audit = audit;
    this.pdf = pdf;
    this.maxFileBytes = maxFileBytes;
    this.defaultQuota = defaultQuota;
    this.keepVersions = keepVersions;
    this.maxLinkDays = maxLinkDays;
  }

  // ---------- чтение ----------

  /** «Мой диск»: корневые узлы, которыми владеет сотрудник. */
  @Transactional(readOnly = true)
  NodeDetails root() {
    requirePermission(Permission.DRIVE_USE);
    var names = staffNames();
    var children =
        nodes
            .findByClinicIdAndParentIdIsNullAndOwnerIdAndDeletedAtIsNull(clinicId(), staffId())
            .stream()
            .map(n -> view(n, "MANAGE", names))
            .sorted(ORDER)
            .toList();
    return new NodeDetails(null, List.of(), children);
  }

  /** Узел, путь и (для папки) содержимое. */
  @Transactional(readOnly = true)
  NodeDetails node(UUID id) {
    requirePermission(Permission.DRIVE_USE);
    var node = live(id);
    var permission = require(node, "VIEW");
    var names = staffNames();
    var children =
        node.folder()
            ? nodes.findByClinicIdAndParentIdAndDeletedAtIsNull(clinicId(), node.id).stream()
                .map(child -> view(child, max(permission, ownShares(child)), names))
                .sorted(ORDER)
                .toList()
            : List.<NodeView>of();
    return new NodeDetails(view(node, permission, names), path(node), children);
  }

  /** Выданное мне напрямую: лично, моей роли или всей клинике. */
  @Transactional(readOnly = true)
  List<NodeView> sharedWithMe() {
    requirePermission(Permission.DRIVE_USE);
    var me = staffId();
    var now = Instant.now();
    var principals = new ArrayList<String>(currentRoles());
    principals.add(me.toString());
    var direct =
        new ArrayList<DriveShare>(
            shares.findByPrincipalTypeInAndPrincipalIdIn(Set.of("USER", "ROLE"), principals));
    direct.addAll(shares.findByPrincipalType("CLINIC"));
    var ids = direct.stream().filter(s -> s.active(now)).map(s -> s.nodeId).distinct().toList();
    if (ids.isEmpty()) return List.of();
    var names = staffNames();
    return nodes.findByClinicIdAndIdInAndDeletedAtIsNull(clinicId(), ids).stream()
        .filter(n -> !me.equals(n.ownerId))
        .filter(n -> !inTrash(n))
        .map(n -> view(n, effective(n), names))
        .filter(v -> v.permission() != null)
        .sorted(ORDER)
        .toList();
  }

  /** Корзина: удалённые узлы, которыми сотрудник может управлять. */
  @Transactional(readOnly = true)
  List<NodeView> trash() {
    requirePermission(Permission.DRIVE_USE);
    var names = staffNames();
    return nodes.findByClinicIdAndDeletedAtIsNotNull(clinicId()).stream()
        .map(n -> view(n, effective(n), names))
        .filter(v -> "MANAGE".equals(v.permission()))
        .sorted(Comparator.comparing(NodeView::deletedAt).reversed())
        .toList();
  }

  @Transactional(readOnly = true)
  List<VersionView> versions(UUID id) {
    requirePermission(Permission.DRIVE_USE);
    var node = live(id);
    require(node, "VIEW");
    var names = staffNames();
    return versions.findByNodeIdOrderByNumberDesc(node.id).stream()
        .map(
            v ->
                new VersionView(
                    v.number,
                    v.sizeBytes,
                    v.sha256,
                    v.contentType,
                    v.uploadedBy,
                    names.get(v.uploadedBy),
                    v.uploadedAt))
        .toList();
  }

  /** Скачивание: расшифровка в сервисе, событие DRIVE_FILE_READ в журнале (только id узла). */
  @Transactional(readOnly = true)
  FileContent content(UUID id, Integer version) {
    requirePermission(Permission.DRIVE_USE);
    var node = live(id);
    require(node, "VIEW");
    return read(node, version, "direct");
  }

  /** Предпросмотр использует те же права и аудит чтения, что скачивание. */
  @Transactional(readOnly = true)
  FileContent preview(UUID id, Integer version) {
    return preview(content(id, version));
  }

  // ---------- изменения ----------

  @Transactional
  NodeView createFolder(FolderRequest request) {
    requirePermission(Permission.DRIVE_USE);
    var name = cleanName(request.name());
    if (request.parentId() != null) requireFolder(request.parentId(), "EDIT");
    requireFreeName(request.parentId(), name, null);
    var folder =
        nodes.save(
            new DriveNode(clinicId(), request.parentId(), DriveNode.FOLDER, name, staffId()));
    audit.record(actor(), "DRIVE_FOLDER_CREATED", "DRIVE_NODE", folder.id, "ALLOWED", null);
    return view(folder, "MANAGE", staffNames());
  }

  @Transactional
  NodeView upload(UUID parentId, String filename, String contentType, byte[] bytes) {
    requirePermission(Permission.DRIVE_USE);
    var name = cleanName(filename);
    if (parentId != null) requireFolder(parentId, "EDIT");
    requireFreeName(parentId, name, null);
    var file = nodes.save(new DriveNode(clinicId(), parentId, DriveNode.FILE, name, staffId()));
    store(file, contentType, bytes);
    audit.record(actor(), "DRIVE_FILE_UPLOADED", "DRIVE_NODE", file.id, "ALLOWED", "v1");
    return view(file, "MANAGE", staffNames());
  }

  @Transactional
  NodeView newVersion(UUID id, String contentType, byte[] bytes) {
    requirePermission(Permission.DRIVE_USE);
    var file = live(id);
    if (file.folder()) throw conflict("NOT_A_FILE", "Only a file has versions");
    var permission = require(file, "EDIT");
    store(file, contentType, bytes);
    audit.record(
        actor(),
        "DRIVE_FILE_UPLOADED",
        "DRIVE_NODE",
        file.id,
        "ALLOWED",
        "v" + file.currentVersion);
    return view(file, permission, staffNames());
  }

  @Transactional
  NodeView update(UUID id, NodeUpdateRequest request) {
    requirePermission(Permission.DRIVE_USE);
    var node = live(id);
    var permission = require(node, "EDIT");
    var targetParent = node.parentId;
    if (Boolean.TRUE.equals(request.moveToRoot()) || request.parentId() != null) {
      require(node, "MANAGE");
      if (Boolean.TRUE.equals(request.moveToRoot())) {
        // В «Мой диск» переносит только владелец: корень показывает только своё.
        if (!node.ownerId.equals(staffId())) {
          throw forbidden("DRIVE_FORBIDDEN", "Only the owner can move this to My drive");
        }
        targetParent = null;
      } else {
        var target = requireFolder(request.parentId(), "EDIT");
        for (var cursor = target; cursor != null; cursor = parentOf(cursor)) {
          if (cursor.id.equals(node.id)) {
            throw conflict("DRIVE_MOVE_INTO_ITSELF", "A folder cannot be moved into itself");
          }
        }
        targetParent = target.id;
      }
    }
    var name = request.name() == null ? node.name : cleanName(request.name());
    if (!Objects.equals(targetParent, node.parentId) || !name.equalsIgnoreCase(node.name)) {
      requireFreeName(targetParent, name, node.id);
    }
    var moved = !Objects.equals(targetParent, node.parentId);
    node.parentId = targetParent;
    node.name = name;
    node.updatedAt = Instant.now();
    audit.record(
        actor(),
        moved ? "DRIVE_NODE_MOVED" : "DRIVE_NODE_RENAMED",
        "DRIVE_NODE",
        node.id,
        "ALLOWED",
        null);
    return view(node, permission, staffNames());
  }

  @Transactional
  NodeView delete(UUID id) {
    requirePermission(Permission.DRIVE_USE);
    var node = live(id);
    require(node, "MANAGE");
    node.deletedAt = Instant.now();
    node.deletedBy = staffId();
    audit.record(actor(), "DRIVE_NODE_TRASHED", "DRIVE_NODE", node.id, "ALLOWED", null);
    return view(node, "MANAGE", staffNames());
  }

  @Transactional
  NodeView restore(UUID id) {
    requirePermission(Permission.DRIVE_USE);
    var node = own(id);
    if (node.deletedAt == null) return view(node, effective(node), staffNames());
    if (!"MANAGE".equals(effective(node))) throw forbidden("DRIVE_FORBIDDEN", "Not allowed");
    var parent = parentOf(node);
    if (parent != null && inTrash(parent)) {
      throw conflict("DRIVE_PARENT_IN_TRASH", "Restore the parent folder first");
    }
    requireFreeName(node.parentId, node.name, node.id);
    node.deletedAt = null;
    node.deletedBy = null;
    audit.record(actor(), "DRIVE_NODE_RESTORED", "DRIVE_NODE", node.id, "ALLOWED", null);
    return view(node, "MANAGE", staffNames());
  }

  // ---------- права ----------

  @Transactional(readOnly = true)
  List<ShareView> shares(UUID id) {
    requirePermission(Permission.DRIVE_USE);
    var node = live(id);
    require(node, "MANAGE");
    var names = staffNames();
    return shares.findByNodeIdOrderByCreatedAt(node.id).stream()
        .map(s -> shareView(s, names, null))
        .toList();
  }

  @Transactional
  ShareView share(UUID id, ShareRequest request) {
    requirePermission(Permission.DRIVE_USE);
    var node = live(id);
    require(node, "MANAGE");
    var type = request.principalType();
    String principal = null;
    switch (type) {
      case "USER" -> {
        principal = request.principalId();
        if (principal == null || !staffNames().containsKey(parseUuid(principal))) {
          throw notFound("STAFF_NOT_FOUND", "Staff member not found");
        }
      }
      case "ROLE" -> {
        principal = request.principalId();
        if (principal == null || !RolePermissions.isStaffRole(principal)) {
          throw conflict("INVALID_ROLE", "Unknown staff role");
        }
      }
      default -> principal = null;
    }
    var expires = request.expiresAt();
    var now = Instant.now();
    if (expires != null && !expires.isAfter(now)) {
      throw conflict("DRIVE_SHARE_EXPIRED", "The expiry must be in the future");
    }
    String token = null;
    String hash = null;
    if ("LINK".equals(type)) {
      // Ссылка — только с обязательным сроком не дольше max-link-days и только на просмотр.
      var limit = now.plus(Duration.ofDays(maxLinkDays));
      if (expires == null || expires.isAfter(limit)) expires = limit;
      if (!"VIEW".equals(request.permission())) {
        throw conflict("DRIVE_LINK_VIEW_ONLY", "A link grants view access only");
      }
      var bytes = new byte[24];
      RANDOM.nextBytes(bytes);
      token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
      hash = sha256(token.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    var share =
        shares.save(
            new DriveShare(
                node.id, type, principal, request.permission(), expires, hash, staffId()));
    audit.record(actor(), "DRIVE_SHARE_CHANGED", "DRIVE_NODE", node.id, "ALLOWED", "grant:" + type);
    return shareView(share, staffNames(), token);
  }

  @Transactional
  void unshare(UUID id, UUID shareId) {
    requirePermission(Permission.DRIVE_USE);
    var node = live(id);
    require(node, "MANAGE");
    var share =
        shares
            .findById(shareId)
            .filter(s -> s.nodeId.equals(node.id))
            .orElseThrow(() -> notFound("DRIVE_SHARE_NOT_FOUND", "Share not found"));
    shares.delete(share);
    audit.record(
        actor(),
        "DRIVE_SHARE_CHANGED",
        "DRIVE_NODE",
        node.id,
        "ALLOWED",
        "revoke:" + share.principalType);
  }

  /** Узел по ссылке: только сотрудник той же клиники после входа, пока ссылка не истекла. */
  @Transactional(readOnly = true)
  NodeDetails link(String token, UUID nodeId) {
    requirePermission(Permission.DRIVE_USE);
    var target = linked(token, nodeId);
    var names = staffNames();
    var children =
        target.folder()
            ? nodes.findByClinicIdAndParentIdAndDeletedAtIsNull(clinicId(), target.id).stream()
                .map(child -> view(child, "VIEW", names))
                .sorted(ORDER)
                .toList()
            : List.<NodeView>of();
    return new NodeDetails(view(target, "VIEW", names), List.of(), children);
  }

  @Transactional(readOnly = true)
  FileContent linkContent(String token, UUID nodeId) {
    requirePermission(Permission.DRIVE_USE);
    var target = linked(token, nodeId);
    return read(target, null, "link");
  }

  @Transactional(readOnly = true)
  FileContent linkPreview(String token, UUID nodeId) {
    return preview(linkContent(token, nodeId));
  }

  /** Сотрудники клиники для выбора в «Поделиться». */
  @Transactional(readOnly = true)
  List<PrincipalView> principals() {
    requirePermission(Permission.DRIVE_USE);
    return jdbc.query(
        "select id, full_name, role from staff_user where clinic_id = ? and active = true"
            + " order by full_name",
        (rs, i) ->
            new PrincipalView(
                rs.getObject("id", UUID.class), rs.getString("full_name"), rs.getString("role")),
        clinicId());
  }

  // ---------- квота ----------

  @Transactional
  QuotaView quota() {
    requirePermission(Permission.DRIVE_USE);
    var quota = quotaRow(clinicId());
    return new QuotaView(quota.bytesUsed, quota.bytesLimit, maxFileBytes);
  }

  @Transactional
  QuotaView setQuota(QuotaRequest request) {
    requirePermission(Permission.DRIVE_ADMIN);
    var quota = quotaRow(clinicId());
    quota.bytesLimit = request.bytesLimit();
    audit.record(actor(), "DRIVE_QUOTA_CHANGED", "CLINIC_DRIVE", clinicId(), "ALLOWED", null);
    return new QuotaView(quota.bytesUsed, quota.bytesLimit, maxFileBytes);
  }

  // ---------- корзина: окончательное удаление ----------

  /** Узлы, пролежавшие в корзине дольше срока, удаляются вместе с объектами в хранилище. */
  @Transactional
  int purge(Instant before) {
    var expired = nodes.findByDeletedAtBefore(before);
    var removedKeys = new ArrayList<String>();
    for (var node : expired) purgeTree(node, removedKeys);
    afterCommit(() -> removedKeys.forEach(storage::delete));
    return expired.size();
  }

  private void purgeTree(DriveNode node, List<String> removedKeys) {
    for (var child : nodes.findByParentId(node.id)) purgeTree(child, removedKeys);
    long freed = 0;
    for (var v : versions.findByNodeIdOrderByNumberDesc(node.id)) {
      removedKeys.add(v.objectKey);
      freed += v.sizeBytes;
      versions.delete(v);
    }
    shares.deleteByNodeId(node.id);
    var quota = quotaRow(node.clinicId);
    quota.bytesUsed = Math.max(0, quota.bytesUsed - freed);
    versions.flush();
    nodes.delete(node);
    nodes.flush();
  }

  // ---------- помощники ----------

  private void store(DriveNode file, String contentType, byte[] bytes) {
    if (bytes.length == 0) throw conflict("DRIVE_EMPTY_FILE", "The file is empty");
    if (bytes.length > maxFileBytes) {
      throw payloadTooLarge("DRIVE_FILE_TOO_LARGE", "The file is larger than the drive limit");
    }
    var quota = quotaRow(clinicId());
    if (quota.bytesUsed + bytes.length > quota.bytesLimit) {
      throw conflict("DRIVE_QUOTA_EXCEEDED", "The clinic drive is full");
    }
    var key = clinicId() + "/" + UUID.randomUUID();
    storage.put(key, encryption.encryptBytes(bytes));
    // Откат транзакции — объект не нужен.
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
              if (status == STATUS_ROLLED_BACK) storage.delete(key);
            }
          });
    }
    var number = file.currentVersion + 1;
    versions.save(
        new DriveVersion(
            file.id,
            number,
            key,
            bytes.length,
            sha256(bytes),
            contentType == null || contentType.length() > 120 ? null : contentType,
            staffId()));
    file.currentVersion = number;
    file.sizeBytes = bytes.length;
    file.updatedAt = Instant.now();
    quota.bytesUsed += bytes.length;
    // Хранятся последние keepVersions версий; старые объекты удаляются после фиксации.
    var all = versions.findByNodeIdOrderByNumberDesc(file.id);
    var stale = new ArrayList<String>();
    for (var i = keepVersions; i < all.size(); i++) {
      var old = all.get(i);
      if (old.number == number) continue;
      stale.add(old.objectKey);
      quota.bytesUsed = Math.max(0, quota.bytesUsed - old.sizeBytes);
      versions.delete(old);
    }
    afterCommit(() -> stale.forEach(storage::delete));
  }

  private FileContent read(DriveNode node, Integer number, String via) {
    if (node.folder()) throw conflict("NOT_A_FILE", "A folder has no content");
    var version =
        versions
            .findByNodeIdAndNumber(node.id, number == null ? node.currentVersion : number)
            .orElseThrow(() -> notFound("DRIVE_VERSION_NOT_FOUND", "Version not found"));
    var bytes = encryption.decryptBytes(storage.get(version.objectKey));
    audit.record(actor(), "DRIVE_FILE_READ", "DRIVE_NODE", node.id, "ALLOWED", via);
    return new FileContent(
        node.name,
        version.contentType == null ? "application/octet-stream" : version.contentType,
        bytes);
  }

  private FileContent preview(FileContent source) {
    var lowerName = source.name().toLowerCase(Locale.ROOT);
    var dot = lowerName.lastIndexOf('.');
    var extension = dot < 0 ? "" : lowerName.substring(dot + 1);
    if ("pdf".equals(extension)) {
      return new FileContent(source.name(), "application/pdf", source.bytes());
    }
    var imageType =
        switch (extension) {
          case "png" -> "image/png";
          case "jpg", "jpeg" -> "image/jpeg";
          case "gif" -> "image/gif";
          case "webp" -> "image/webp";
          default -> null;
        };
    if (imageType != null) return new FileContent(source.name(), imageType, source.bytes());
    if ("docx".equals(extension) || "xlsx".equals(extension)) {
      var converted =
          pdf.convert(source.bytes(), extension)
              .orElseThrow(
                  () ->
                      conflict(
                          "PREVIEW_CONVERSION_UNAVAILABLE",
                          "LibreOffice is not configured for document preview"));
      var baseName = source.name().substring(0, source.name().lastIndexOf('.'));
      return new FileContent(baseName + ".pdf", "application/pdf", converted);
    }
    throw conflict("PREVIEW_UNSUPPORTED", "This file format cannot be previewed");
  }

  private DriveNode linked(String token, UUID nodeId) {
    var hash = sha256(token.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    var share =
        shares
            .findByLinkTokenHash(hash)
            .filter(s -> "LINK".equals(s.principalType))
            .filter(s -> s.active(Instant.now()))
            .orElseThrow(() -> notFound("DRIVE_LINK_NOT_FOUND", "Link not found or expired"));
    var root = live(share.nodeId);
    if (inTrash(root)) throw notFound("DRIVE_LINK_NOT_FOUND", "Link not found or expired");
    if (nodeId == null || nodeId.equals(root.id)) return root;
    var target = live(nodeId);
    for (var cursor = parentOf(target); cursor != null; cursor = parentOf(cursor)) {
      if (cursor.id.equals(root.id)) return target;
    }
    throw notFound("DRIVE_NODE_NOT_FOUND", "Not found");
  }

  /** Эффективное право сотрудника на узел или null. */
  String effective(DriveNode node) {
    var me = staffId();
    var roles = currentRoles();
    var now = Instant.now();
    var chain = new ArrayList<DriveNode>();
    for (var cursor = node; cursor != null && chain.size() < 64; cursor = parentOf(cursor)) {
      if (me.equals(cursor.ownerId)) return "MANAGE";
      chain.add(cursor);
    }
    String best = null;
    for (var share : shares.findByNodeIdIn(chain.stream().map(n -> n.id).toList())) {
      if (!share.active(now)) continue;
      var matches =
          switch (share.principalType) {
            case "USER" -> me.toString().equals(share.principalId);
            case "ROLE" -> roles.contains(share.principalId);
            case "CLINIC" -> true;
            default -> false;
          };
      if (matches) best = max(best, share.permission);
    }
    return best;
  }

  /** Право по выданным на сам узел записям (для расширения унаследованного). */
  private String ownShares(DriveNode node) {
    if (staffId().equals(node.ownerId)) return "MANAGE";
    var me = staffId().toString();
    var roles = currentRoles();
    var now = Instant.now();
    String best = null;
    for (var share : shares.findByNodeIdIn(List.of(node.id))) {
      if (!share.active(now)) continue;
      var matches =
          ("USER".equals(share.principalType) && me.equals(share.principalId))
              || ("ROLE".equals(share.principalType) && roles.contains(share.principalId))
              || "CLINIC".equals(share.principalType);
      if (matches) best = max(best, share.permission);
    }
    return best;
  }

  private String require(DriveNode node, String needed) {
    var permission = effective(node);
    if (permission == null) {
      // Без права узел выглядит как несуществующий.
      throw notFound("DRIVE_NODE_NOT_FOUND", "Not found");
    }
    if (RANK.indexOf(permission) < RANK.indexOf(needed)) {
      audit.record(actor(), "DRIVE_ACCESS", "DRIVE_NODE", node.id, "DENIED", "need:" + needed);
      throw forbidden("DRIVE_FORBIDDEN", "Not enough rights for this file or folder");
    }
    return permission;
  }

  private DriveNode requireFolder(UUID id, String needed) {
    var folder = live(id);
    if (!folder.folder()) throw conflict("NOT_A_FOLDER", "The target is not a folder");
    require(folder, needed);
    return folder;
  }

  private void requireFreeName(UUID parentId, String name, UUID except) {
    var siblings =
        parentId == null
            ? nodes.findByClinicIdAndParentIdIsNullAndOwnerIdAndDeletedAtIsNull(
                clinicId(), staffId())
            : nodes.findByClinicIdAndParentIdAndDeletedAtIsNull(clinicId(), parentId);
    var taken =
        siblings.stream().anyMatch(n -> !n.id.equals(except) && n.name.equalsIgnoreCase(name));
    if (taken) throw conflict("DRIVE_NAME_TAKEN", "A file or folder with this name already exists");
  }

  /** Живой узел своей клиники: не в корзине и не внутри удалённой папки. */
  private DriveNode live(UUID id) {
    var node = own(id);
    if (inTrash(node)) throw notFound("DRIVE_NODE_NOT_FOUND", "Not found");
    return node;
  }

  private DriveNode own(UUID id) {
    return nodes
        .findByIdAndClinicId(id, clinicId())
        .orElseThrow(() -> notFound("DRIVE_NODE_NOT_FOUND", "Not found"));
  }

  private boolean inTrash(DriveNode node) {
    for (var cursor = node; cursor != null; cursor = parentOf(cursor)) {
      if (cursor.deletedAt != null) return true;
    }
    return false;
  }

  private DriveNode parentOf(DriveNode node) {
    return node.parentId == null ? null : nodes.findById(node.parentId).orElse(null);
  }

  private List<NodeDetails.Crumb> path(DriveNode node) {
    var crumbs = new ArrayList<NodeDetails.Crumb>();
    for (var cursor = parentOf(node); cursor != null; cursor = parentOf(cursor)) {
      if (effective(cursor) == null) break;
      crumbs.add(0, new NodeDetails.Crumb(cursor.id, cursor.name));
    }
    return crumbs;
  }

  private DriveQuota quotaRow(UUID clinicId) {
    return quotas
        .findById(clinicId)
        .orElseGet(() -> quotas.saveAndFlush(new DriveQuota(clinicId, defaultQuota)));
  }

  private NodeView view(DriveNode n, String permission, Map<UUID, String> names) {
    var shared = !shares.findByNodeIdIn(List.of(n.id)).isEmpty();
    return new NodeView(
        n.id,
        n.parentId,
        n.kind,
        n.name,
        n.ownerId,
        names.get(n.ownerId),
        n.sizeBytes,
        n.currentVersion,
        n.createdAt,
        n.updatedAt,
        n.deletedAt,
        permission,
        shared);
  }

  private ShareView shareView(DriveShare s, Map<UUID, String> names, String token) {
    var label =
        switch (s.principalType) {
          case "USER" -> names.getOrDefault(parseUuid(s.principalId), s.principalId);
          case "ROLE" -> s.principalId;
          default -> null;
        };
    return new ShareView(
        s.id, s.principalType, s.principalId, label, s.permission, s.expiresAt, s.createdAt, token);
  }

  private Map<UUID, String> staffNames() {
    var result = new HashMap<UUID, String>();
    jdbc.query(
        "select id, full_name from staff_user where clinic_id = ?",
        rs -> {
          result.put(rs.getObject("id", UUID.class), rs.getString("full_name"));
        },
        clinicId());
    return result;
  }

  private static UUID parseUuid(String value) {
    try {
      return UUID.fromString(value);
    } catch (IllegalArgumentException ex) {
      return null;
    }
  }

  /** Автор в журнале — тот же, что во всём журнале (у врача — карточка врача). */
  private static UUID actor() {
    return ru.quietclinic.platform.PlatformConfig.actorId();
  }

  static String max(String left, String right) {
    if (left == null) return right;
    if (right == null) return left;
    return RANK.indexOf(left) >= RANK.indexOf(right) ? left : right;
  }

  /** Имя без пути и управляющих символов, не длиннее 255. */
  static String cleanName(String raw) {
    var name = raw == null ? "" : raw;
    name = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);
    name = name.replaceAll("\\p{Cntrl}", "").trim();
    if (name.isEmpty() || name.equals(".") || name.equals("..")) {
      throw conflict("DRIVE_INVALID_NAME", "Enter a name");
    }
    return name.length() > 255 ? name.substring(0, 255) : name;
  }

  private static String sha256(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException(ex);
    }
  }

  private static void afterCommit(Runnable action) {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              action.run();
            }
          });
    } else {
      action.run();
    }
  }

  /** Папки сверху, затем по имени. */
  private static final Comparator<NodeView> ORDER =
      Comparator.comparing((NodeView v) -> !"FOLDER".equals(v.kind()))
          .thenComparing(v -> v.name().toLowerCase(Locale.ROOT));
}
