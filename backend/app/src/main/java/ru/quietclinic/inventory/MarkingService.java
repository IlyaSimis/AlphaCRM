package ru.quietclinic.inventory;

import static ru.quietclinic.platform.PlatformConfig.actorId;
import static ru.quietclinic.platform.PlatformConfig.clinicId;
import static ru.quietclinic.platform.PlatformConfig.conflict;
import static ru.quietclinic.platform.PlatformConfig.notFound;
import static ru.quietclinic.platform.PlatformConfig.requirePermission;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import ru.quietclinic.audit.AuditService;
import ru.quietclinic.platform.Permission;

/** Сканирование марок, реестр марок и очередь операций маркировки. */
@Service
class MarkingService {
  private final MarkingCodeRepository codes;
  private final MarkingOperationRepository operations;
  private final ItemRepository items;
  private final MarkingRelay relay;
  private final AuditService audit;

  MarkingService(
      MarkingCodeRepository codes,
      MarkingOperationRepository operations,
      ItemRepository items,
      MarkingRelay relay,
      AuditService audit) {
    this.codes = codes;
    this.operations = operations;
    this.items = items;
    this.relay = relay;
    this.audit = audit;
  }

  /** Код со сканера (GS1) или уже готовый SGTIN (14 цифр GTIN + серийный номер). */
  static Gs1.Parsed read(String raw) {
    String text = raw.strip();
    boolean gs1 =
        text.startsWith("(")
            || text.startsWith("]")
            || text.indexOf(Gs1.GS) >= 0
            || (text.startsWith("01") && text.length() > 18 && text.startsWith("21", 16));
    if (gs1) return Gs1.parse(text);
    if (!text.matches("\\d{14}[\\x21-\\x7E]{1,20}")) return Gs1.parse(text);
    return new Gs1.Parsed(text.substring(0, 14), text.substring(14), "", null);
  }

  @Transactional(readOnly = true)
  ScanView scan(ScanRequest request) {
    requirePermission(Permission.INVENTORY_DOCUMENT_WRITE, Permission.INVENTORY_CATALOG_READ);
    Gs1.Parsed parsed = read(request.code());
    Optional<Item> item =
        items.findByClinicIdAndGtin(clinicId(), parsed.gtin()).stream().findFirst();
    Optional<MarkingCode> known = codes.findByClinicIdAndSgtin(clinicId(), parsed.sgtin());
    return new ScanView(
        parsed.sgtin(),
        parsed.gtin(),
        parsed.serial(),
        parsed.batch(),
        parsed.expiresOn(),
        item.map(i -> i.id).orElse(null),
        item.map(i -> i.name).orElse(null),
        known.map(c -> c.status).orElse(null),
        known.map(c -> c.warehouseId).orElse(null));
  }

  @Transactional(readOnly = true)
  List<MarkingCodeView> codes(String status, UUID itemId) {
    requirePermission(Permission.INVENTORY_STOCK_READ);
    List<MarkingCode> rows =
        status == null || status.isBlank()
            ? codes.findTop500ByClinicIdOrderByUpdatedAtDesc(clinicId())
            : codes.findTop500ByClinicIdAndStatusOrderByUpdatedAtDesc(clinicId(), status);
    Map<UUID, String> names = new HashMap<>();
    items.findByClinicIdOrderByName(clinicId()).forEach(i -> names.put(i.id, i.name));
    return rows.stream()
        .filter(c -> itemId == null || itemId.equals(c.itemId))
        .map(
            c ->
                new MarkingCodeView(
                    c.sgtin,
                    c.itemId,
                    names.getOrDefault(c.itemId, ""),
                    c.batch,
                    c.expiresOn,
                    c.status,
                    c.warehouseId,
                    c.updatedAt))
        .toList();
  }

  @Transactional(readOnly = true)
  List<MarkingOperationView> operations(String status) {
    requirePermission(Permission.MARKING_STATUS_READ);
    List<MarkingOperation> rows =
        status == null || status.isBlank()
            ? operations.findTop200ByClinicIdOrderByCreatedAtDesc(clinicId())
            : operations.findTop200ByClinicIdAndStatusOrderByCreatedAtDesc(clinicId(), status);
    return rows.stream().map(MarkingOperationView::of).toList();
  }

  @Transactional(readOnly = true)
  MarkingStatusView status() {
    requirePermission(Permission.MARKING_STATUS_READ);
    return new MarkingStatusView(
        relay.adapter(),
        operations.countByClinicIdAndStatus(clinicId(), MarkingOperation.PENDING),
        operations.countByClinicIdAndStatus(clinicId(), MarkingOperation.ERROR));
  }

  /** Повторить сейчас: ожидающая операция — сразу, ошибочная — снова в очередь. */
  @Transactional
  MarkingOperationView retry(UUID id) {
    requirePermission(Permission.MARKING_MANAGE);
    MarkingOperation op =
        operations
            .findByIdAndClinicId(id, clinicId())
            .orElseThrow(
                () -> notFound("MARKING_OPERATION_NOT_FOUND", "Marking operation not found"));
    if (MarkingOperation.DONE.equals(op.status)) {
      throw conflict("MARKING_OPERATION_DONE", "The operation is already accepted");
    }
    op.status = MarkingOperation.PENDING;
    op.nextAttemptAt = Instant.now();
    op.attempts = 0;
    operations.saveAndFlush(op);
    audit.record(
        actorId(), "MARKING_RETRY_REQUESTED", "MARKING_OPERATION", op.id, "ALLOWED", op.type);
    UUID opId = op.id;
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            relay.process(opId);
          }
        });
    return MarkingOperationView.of(op);
  }
}
