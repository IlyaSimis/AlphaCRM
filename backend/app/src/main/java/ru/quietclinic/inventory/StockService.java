package ru.quietclinic.inventory;

import static ru.quietclinic.platform.PlatformConfig.actorId;
import static ru.quietclinic.platform.PlatformConfig.can;
import static ru.quietclinic.platform.PlatformConfig.clinicId;
import static ru.quietclinic.platform.PlatformConfig.conflict;
import static ru.quietclinic.platform.PlatformConfig.notFound;
import static ru.quietclinic.platform.PlatformConfig.requirePermission;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import ru.quietclinic.audit.AuditService;
import ru.quietclinic.platform.DomainEvents;
import ru.quietclinic.platform.Permission;

/**
 * Накладные и остатки. Проведение: приход увеличивает остаток и ставит марки в приёмку, расход,
 * списание и возврат уменьшают остаток и ставят марки на выбытие, перемещение переносит остаток и
 * марки. Остаток не уходит в минус, кроме явного разрешения для расхода — тогда публикуется {@code
 * StockShortage}. Операции маркировки уходят оператору после фиксации транзакции.
 */
@Service
class StockService {
  private final StockDocumentRepository documents;
  private final StockLineRepository lines;
  private final MarkingCodeRepository codes;
  private final MarkingOperationRepository operations;
  private final ItemRepository items;
  private final CatalogService catalog;
  private final MarkingRelay relay;
  private final DomainEvents events;
  private final JdbcTemplate jdbc;
  private final AuditService audit;

  StockService(
      StockDocumentRepository documents,
      StockLineRepository lines,
      MarkingCodeRepository codes,
      MarkingOperationRepository operations,
      ItemRepository items,
      CatalogService catalog,
      MarkingRelay relay,
      DomainEvents events,
      JdbcTemplate jdbc,
      AuditService audit) {
    this.documents = documents;
    this.lines = lines;
    this.codes = codes;
    this.operations = operations;
    this.items = items;
    this.catalog = catalog;
    this.relay = relay;
    this.events = events;
    this.jdbc = jdbc;
    this.audit = audit;
  }

  // ---------- накладные ----------

  @Transactional(readOnly = true)
  List<DocumentView> list(String type, String status) {
    requirePermission(Permission.INVENTORY_DOCUMENT_WRITE);
    return documents.findTop200ByClinicIdOrderByCreatedAtDesc(clinicId()).stream()
        .filter(d -> type == null || type.isBlank() || d.type.equals(type))
        .filter(d -> status == null || status.isBlank() || d.status.equals(status))
        .map(d -> view(d, List.of()))
        .toList();
  }

  @Transactional(readOnly = true)
  DocumentView get(UUID id) {
    requirePermission(Permission.INVENTORY_DOCUMENT_WRITE);
    return view(document(id), List.of());
  }

  @Transactional
  DocumentView create(DocumentRequest request) {
    requirePermission(Permission.INVENTORY_DOCUMENT_WRITE);
    return view(createDraft(request), List.of());
  }

  /** Черновик без проверки прав — для потребителя события о завершённом приёме. */
  StockDocument createDraft(DocumentRequest request) {
    var existing = documents.findByClinicIdAndIdempotencyKey(clinicId(), request.idempotencyKey());
    if (existing.isPresent()) return existing.get();
    var document =
        new StockDocument(
            clinicId(),
            request.type(),
            request.documentDate() == null ? LocalDate.now() : request.documentDate(),
            request.idempotencyKey(),
            actorId());
    applyHeader(document, request);
    documents.saveAndFlush(document);
    replaceLines(document, request.lines());
    audit.record(
        actorId(),
        "STOCK_DOCUMENT_CREATED",
        "STOCK_DOCUMENT",
        document.id,
        "ALLOWED",
        document.type);
    return document;
  }

  @Transactional
  DocumentView update(UUID id, DocumentRequest request) {
    requirePermission(Permission.INVENTORY_DOCUMENT_WRITE);
    var document = document(id);
    requireDraft(document);
    if (!document.type.equals(request.type())) {
      throw conflict("DOCUMENT_TYPE_FIXED", "The document type cannot be changed");
    }
    if (request.documentDate() != null) document.documentDate = request.documentDate();
    applyHeader(document, request);
    lines.deleteByDocumentId(document.id);
    lines.flush();
    replaceLines(document, request.lines());
    audit.record(
        actorId(), "STOCK_DOCUMENT_UPDATED", "STOCK_DOCUMENT", document.id, "ALLOWED", null);
    return view(document, List.of());
  }

  @Transactional
  DocumentView cancel(UUID id) {
    requirePermission(Permission.INVENTORY_DOCUMENT_WRITE);
    var document = document(id);
    if (StockDocument.POSTED.equals(document.status)) {
      throw conflict(
          "DOCUMENT_POSTED", "A posted document cannot be cancelled; create a correcting one");
    }
    if (!StockDocument.CANCELLED.equals(document.status)) {
      document.status = StockDocument.CANCELLED;
      audit.record(
          actorId(), "STOCK_DOCUMENT_CANCELLED", "STOCK_DOCUMENT", document.id, "ALLOWED", null);
    }
    return view(document, List.of());
  }

  @Transactional
  DocumentView post(UUID id, boolean allowNegative) {
    requirePermission(Permission.INVENTORY_DOCUMENT_WRITE);
    var document = document(id);
    if (allowNegative) {
      requirePermission(Permission.INVENTORY_ALLOW_NEGATIVE);
      if (!StockDocument.CONSUMPTION.equals(document.type)) {
        throw conflict(
            "NEGATIVE_NOT_ALLOWED", "Only consumption may be posted beyond the available stock");
      }
    }
    return postDocument(document, allowNegative);
  }

  /** Проведение без проверки прав (вызывают post и потребитель события). Повтор безопасен. */
  DocumentView postDocument(StockDocument document, boolean allowNegative) {
    if (StockDocument.POSTED.equals(document.status)) return view(document, List.of());
    requireDraft(document);
    var rows = lines.findByDocumentIdOrderByLineNo(document.id);
    if (rows.isEmpty()) throw conflict("DOCUMENT_EMPTY", "The document has no lines");
    checkWarehouses(document);
    var itemsById =
        items.findByClinicIdAndIdIn(clinicId(), rows.stream().map(l -> l.itemId).toList()).stream()
            .collect(Collectors.toMap(i -> i.id, Function.identity()));
    var shortages = new ArrayList<DocumentView.Shortage>();
    var outgoing = new LinkedHashMap<String, Set<String>>();
    for (var line : rows) {
      var item = itemsById.get(line.itemId);
      if (item == null) throw notFound("ITEM_NOT_FOUND", "Item not found");
      if (item.marked) {
        postMarked(document, line, item, outgoing);
      } else if (!line.codes.isEmpty()) {
        throw conflict("ITEM_NOT_MARKED", "Marking codes are given for an item without marking");
      } else {
        postPlain(document, line, item, allowNegative, shortages);
      }
    }
    var created = new ArrayList<UUID>();
    outgoing.forEach(
        (type, sgtins) -> {
          if (sgtins.isEmpty()) return;
          var op = operations.save(new MarkingOperation(clinicId(), type, document.id, sgtins));
          created.add(op.id);
        });
    document.status = StockDocument.POSTED;
    document.postedBy = actorId();
    document.postedAt = Instant.now();
    documents.saveAndFlush(document);
    audit.record(
        actorId(),
        "STOCK_DOCUMENT_POSTED",
        "STOCK_DOCUMENT",
        document.id,
        "ALLOWED",
        document.type);
    for (var shortage : shortages) {
      var payload = new LinkedHashMap<String, Object>();
      payload.put("documentId", document.id.toString());
      payload.put(
          "appointmentId",
          document.appointmentId == null ? null : document.appointmentId.toString());
      payload.put("itemId", shortage.itemId().toString());
      payload.put("itemName", shortage.itemName());
      payload.put("missing", shortage.missing().stripTrailingZeros().toPlainString());
      events.publish("StockShortage", payload);
    }
    afterCommit(created);
    return view(document, shortages);
  }

  private void postMarked(
      StockDocument document, StockLine line, Item item, Map<String, Set<String>> outgoing) {
    if (line.quantity.stripTrailingZeros().scale() > 0
        || line.quantity.intValueExact() != line.codes.size()) {
      throw conflict(
          "MARKING_CODES_MISMATCH", "Scan one marking code for every package of a marked item");
    }
    var existing =
        codes.findByClinicIdAndSgtinIn(clinicId(), line.codes).stream()
            .collect(Collectors.toMap(c -> c.sgtin, Function.identity()));
    if (StockDocument.RECEIPT.equals(document.type)) {
      for (var sgtin : line.codes) {
        var found = existing.get(sgtin);
        if (found != null && !Set.of("RETURNED", "ERROR").contains(found.status)) {
          throw conflict("MARKING_CODE_DUPLICATE", "This package is already in stock");
        }
        var parsed = sgtinParts(sgtin, item);
        if (found != null) {
          found.move("PENDING_ACCEPTANCE", document.id);
          found.warehouseId = document.toWarehouseId;
          found.batch = line.batch;
          found.expiresOn = line.expiresOn;
        } else {
          codes.save(
              new MarkingCode(
                  clinicId(),
                  parsed,
                  item.id,
                  line.batch,
                  line.expiresOn,
                  document.toWarehouseId,
                  document.id));
        }
        outgoing
            .computeIfAbsent(MarkingOperation.ACCEPTANCE, k -> new LinkedHashSet<>())
            .add(sgtin);
      }
      addBalance(item.id, document.toWarehouseId, line.batch, line.expiresOn, line.quantity);
      return;
    }
    var byBatch = new LinkedHashMap<String, BigDecimal>();
    for (var sgtin : line.codes) {
      var code = existing.get(sgtin);
      if (code == null
          || !"IN_STOCK".equals(code.status)
          || !document.fromWarehouseId.equals(code.warehouseId)
          || !item.id.equals(code.itemId)) {
        throw conflict(
            "MARKING_CODE_NOT_IN_STOCK", "The package is not in stock at this warehouse");
      }
      byBatch.merge(code.batch, BigDecimal.ONE, BigDecimal::add);
      switch (document.type) {
        case StockDocument.TRANSFER -> {
          code.warehouseId = document.toWarehouseId;
          code.move("IN_STOCK", document.id);
        }
        case StockDocument.CONSUMPTION -> {
          code.move("WITHDRAWAL_PENDING", document.id);
          outgoing
              .computeIfAbsent(MarkingOperation.WITHDRAW_MEDICAL_USE, k -> new LinkedHashSet<>())
              .add(sgtin);
        }
        case StockDocument.WRITE_OFF -> {
          code.move("WRITE_OFF_PENDING", document.id);
          outgoing
              .computeIfAbsent(MarkingOperation.WRITE_OFF, k -> new LinkedHashSet<>())
              .add(sgtin);
        }
        case StockDocument.RETURN_TO_SUPPLIER -> {
          code.move("RETURN_PENDING", document.id);
          outgoing
              .computeIfAbsent(MarkingOperation.RETURN_TO_SUPPLIER, k -> new LinkedHashSet<>())
              .add(sgtin);
        }
        default -> throw conflict("INVALID_DOCUMENT_TYPE", "Unknown document type");
      }
    }
    byBatch.forEach(
        (batch, quantity) -> {
          decrease(item, document.fromWarehouseId, batch, quantity, false);
          if (StockDocument.TRANSFER.equals(document.type)) {
            addBalance(item.id, document.toWarehouseId, batch, null, quantity);
          }
        });
  }

  private void postPlain(
      StockDocument document,
      StockLine line,
      Item item,
      boolean allowNegative,
      List<DocumentView.Shortage> shortages) {
    if (StockDocument.RECEIPT.equals(document.type)) {
      addBalance(item.id, document.toWarehouseId, line.batch, line.expiresOn, line.quantity);
      return;
    }
    var allocated =
        decrease(item, document.fromWarehouseId, line.batch, line.quantity, allowNegative);
    var missing =
        line.quantity.subtract(
            allocated.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add));
    if (missing.signum() > 0) shortages.add(new DocumentView.Shortage(item.id, item.name, missing));
    if (StockDocument.TRANSFER.equals(document.type)) {
      allocated.forEach(
          (batch, quantity) -> addBalance(item.id, document.toWarehouseId, batch, null, quantity));
    }
  }

  /**
   * Уменьшает остаток. Без партии — по партиям с ближайшим сроком (FIFO). Возвращает списанное по
   * партиям из наличия; недостающее при allowNegative уходит в минус на партии без номера.
   */
  private Map<String, BigDecimal> decrease(
      Item item, UUID warehouseId, String batch, BigDecimal quantity, boolean allowNegative) {
    var balances =
        jdbc.query(
            "select batch, quantity from inventory.stock_balance where item_id = ? and warehouse_id"
                + " = ?"
                + (batch.isBlank() ? "" : " and batch = ?")
                + " order by expires_on nulls last, batch",
            (rs, i) -> Map.entry(rs.getString("batch"), rs.getBigDecimal("quantity")),
            batch.isBlank()
                ? new Object[] {item.id, warehouseId}
                : new Object[] {item.id, warehouseId, batch});
    var available =
        balances.stream()
            .map(Map.Entry::getValue)
            .filter(q -> q.signum() > 0)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    if (available.compareTo(quantity) < 0 && !allowNegative) {
      throw conflict("INSUFFICIENT_STOCK", "Not enough stock: " + item.name);
    }
    var allocated = new LinkedHashMap<String, BigDecimal>();
    var left = quantity;
    for (var row : balances) {
      if (left.signum() <= 0) break;
      if (row.getValue().signum() <= 0) continue;
      var take = row.getValue().min(left);
      addBalance(item.id, warehouseId, row.getKey(), null, take.negate());
      allocated.merge(row.getKey(), take, BigDecimal::add);
      left = left.subtract(take);
    }
    if (left.signum() > 0) addBalance(item.id, warehouseId, batch, null, left.negate());
    return allocated;
  }

  private void addBalance(
      UUID itemId, UUID warehouseId, String batch, LocalDate expiresOn, BigDecimal delta) {
    var key = batch == null ? "" : batch;
    var updated =
        jdbc.update(
            "update inventory.stock_balance set quantity = quantity + ?,"
                + " expires_on = coalesce(expires_on, ?)"
                + " where item_id = ? and warehouse_id = ? and batch = ?",
            delta,
            expiresOn == null ? null : Date.valueOf(expiresOn),
            itemId,
            warehouseId,
            key);
    if (updated == 0) {
      jdbc.update(
          "insert into inventory.stock_balance(clinic_id, item_id, warehouse_id, batch, expires_on,"
              + " quantity) values (?, ?, ?, ?, ?, ?)",
          clinicId(),
          itemId,
          warehouseId,
          key,
          expiresOn == null ? null : Date.valueOf(expiresOn),
          delta);
    }
  }

  private void afterCommit(List<UUID> operationIds) {
    if (operationIds.isEmpty()) return;
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              operationIds.forEach(relay::process);
            }
          });
    } else {
      operationIds.forEach(relay::process);
    }
  }

  // ---------- остатки ----------

  @Transactional(readOnly = true)
  List<BalanceView> balances(UUID warehouseId, UUID categoryId, String query, boolean includeZero) {
    requirePermission(Permission.INVENTORY_STOCK_READ);
    var sql =
        new StringBuilder(
            "select b.item_id, i.name as item_name, i.category_id, c.name as category_name, c.kind,"
                + " i.unit, i.marked, b.warehouse_id, w.name as warehouse_name, b.batch,"
                + " b.expires_on, b.quantity,"
                + " (select count(*) from inventory.marking_code m where m.item_id = b.item_id"
                + "  and m.warehouse_id = b.warehouse_id and m.batch = b.batch"
                + "  and m.status = 'IN_STOCK') as codes_in_stock"
                + " from inventory.stock_balance b"
                + " join inventory.item i on i.id = b.item_id"
                + " join inventory.item_category c on c.id = i.category_id"
                + " join inventory.warehouse w on w.id = b.warehouse_id"
                + " where b.clinic_id = ?");
    var args = new ArrayList<Object>(List.of(clinicId()));
    if (!includeZero) sql.append(" and b.quantity <> 0");
    if (warehouseId != null) {
      sql.append(" and b.warehouse_id = ?");
      args.add(warehouseId);
    }
    if (categoryId != null) {
      sql.append(" and (i.category_id = ? or c.parent_id = ?)");
      args.add(categoryId);
      args.add(categoryId);
    }
    if (query != null && !query.isBlank()) {
      sql.append(" and lower(i.name) like ?");
      args.add("%" + query.trim().toLowerCase(Locale.ROOT) + "%");
    }
    sql.append(" order by i.name, w.name, b.expires_on nulls last, b.batch");
    return jdbc.query(
        sql.toString(),
        (rs, i) ->
            new BalanceView(
                rs.getObject("item_id", UUID.class),
                rs.getString("item_name"),
                rs.getObject("category_id", UUID.class),
                rs.getString("category_name"),
                rs.getString("kind"),
                rs.getString("unit"),
                rs.getBoolean("marked"),
                rs.getObject("warehouse_id", UUID.class),
                rs.getString("warehouse_name"),
                rs.getString("batch"),
                rs.getObject("expires_on", LocalDate.class),
                rs.getBigDecimal("quantity"),
                rs.getLong("codes_in_stock")),
        args.toArray());
  }

  // ---------- помощники ----------

  private void applyHeader(StockDocument document, DocumentRequest request) {
    document.supplier = blankToNull(request.supplier());
    document.note = blankToNull(request.note());
    document.appointmentId = request.appointmentId();
    document.fromWarehouseId = request.fromWarehouseId();
    document.toWarehouseId = request.toWarehouseId();
    var defaults = catalog.defaultWarehouse(clinicId());
    if (document.decreases() && document.fromWarehouseId == null) {
      document.fromWarehouseId = defaults.id;
    }
    if (document.increases() && document.toWarehouseId == null) {
      document.toWarehouseId = defaults.id;
    }
    if (!document.increases()) document.toWarehouseId = null;
    if (!document.decreases()) document.fromWarehouseId = null;
    checkWarehouses(document);
  }

  private void checkWarehouses(StockDocument document) {
    if (document.fromWarehouseId != null) catalog.warehouse(document.fromWarehouseId);
    if (document.toWarehouseId != null) catalog.warehouse(document.toWarehouseId);
    if (StockDocument.TRANSFER.equals(document.type)
        && document.fromWarehouseId.equals(document.toWarehouseId)) {
      throw conflict("SAME_WAREHOUSE", "Transfer needs two different warehouses");
    }
  }

  private void replaceLines(StockDocument document, List<DocumentRequest.Line> requested) {
    var number = 0;
    var seen = new LinkedHashSet<String>();
    for (var line : requested) {
      var item = catalog.item(line.itemId());
      if (!item.active) throw conflict("ITEM_INACTIVE", "The item is switched off");
      var sgtins = new LinkedHashSet<String>();
      var batch = line.batch() == null ? "" : line.batch().trim();
      var expires = line.expiresOn();
      for (var raw : line.codes() == null ? List.<String>of() : line.codes()) {
        if (!item.marked) {
          throw conflict("ITEM_NOT_MARKED", "Marking codes are given for an item without marking");
        }
        var parsed = MarkingService.read(raw);
        if (!parsed.gtin().equals(item.gtin)) {
          throw conflict("MARKING_CODE_ITEM_MISMATCH", "The code belongs to a different item");
        }
        if (!seen.add(parsed.sgtin())) {
          throw conflict("MARKING_CODE_REPEATED", "The same package is scanned twice");
        }
        sgtins.add(parsed.sgtin());
        if (batch.isEmpty() && !parsed.batch().isEmpty()) batch = parsed.batch();
        if (expires == null) expires = parsed.expiresOn();
      }
      lines.save(
          new StockLine(document.id, ++number, item.id, line.quantity(), batch, expires, sgtins));
    }
    lines.flush();
  }

  private static Gs1.Parsed sgtinParts(String sgtin, Item item) {
    return new Gs1.Parsed(sgtin.substring(0, 14), sgtin.substring(14), "", null);
  }

  private static void requireDraft(StockDocument document) {
    if (!StockDocument.DRAFT.equals(document.status)) {
      throw conflict("DOCUMENT_NOT_DRAFT", "Only a draft document can be changed or posted");
    }
  }

  StockDocument document(UUID id) {
    return documents
        .findByIdAndClinicId(id, clinicId())
        .orElseThrow(() -> notFound("STOCK_DOCUMENT_NOT_FOUND", "Stock document not found"));
  }

  DocumentView view(StockDocument d, List<DocumentView.Shortage> shortages) {
    var rows = lines.findByDocumentIdOrderByLineNo(d.id);
    var itemsById = new HashMap<UUID, Item>();
    items
        .findByClinicIdAndIdIn(d.clinicId, rows.stream().map(l -> l.itemId).toList())
        .forEach(i -> itemsById.put(i.id, i));
    // Техподдержке и врачу накладные не отдаются вовсе (нет inventory.document.write); связь с
    // приёмом видят только те, кто проводит документы.
    var appointment = can(Permission.INVENTORY_DOCUMENT_WRITE) ? d.appointmentId : null;
    return new DocumentView(
        d.id,
        d.type,
        d.status,
        d.documentDate,
        d.supplier,
        appointment,
        d.fromWarehouseId,
        d.toWarehouseId,
        d.note,
        d.createdAt,
        d.postedAt,
        rows.stream()
            .map(
                l -> {
                  var item = itemsById.get(l.itemId);
                  return new DocumentView.LineView(
                      l.id,
                      l.itemId,
                      item == null ? "" : item.name,
                      item == null ? "" : item.unit,
                      item != null && item.marked,
                      l.quantity,
                      l.batch,
                      l.expiresOn,
                      List.copyOf(l.codes));
                })
            .toList(),
        shortages);
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
