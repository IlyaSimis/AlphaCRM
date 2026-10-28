package ru.quietclinic.inventory;

import static ru.quietclinic.platform.PlatformConfig.clinicId;
import static ru.quietclinic.platform.PlatformConfig.conflict;
import static ru.quietclinic.platform.PlatformConfig.requirePermission;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.quietclinic.platform.Permission;

/**
 * Приход в одно действие: недостающие позиции заводятся в основной категории своего вида, накладная
 * создаётся и сразу проводится. Если у маркируемой позиции не хватает марок, накладная остаётся
 * черновиком — марки досканируют в ней.
 */
@Service
class ReceiptService {
  private final CatalogService catalog;
  private final StockService stock;

  ReceiptService(CatalogService catalog, StockService stock) {
    this.catalog = catalog;
    this.stock = stock;
  }

  @Transactional
  ReceiptView receive(ReceiptRequest request) {
    requirePermission(Permission.INVENTORY_DOCUMENT_WRITE);
    var created = new HashMap<String, Item>();
    var lines = new ArrayList<DocumentRequest.Line>();
    var waiting = new ArrayList<String>();
    for (var line : request.lines()) {
      var item = resolve(line, created);
      var codes = line.codes() == null ? List.<String>of() : line.codes();
      if (item.marked && codes.size() != line.quantity().stripTrailingZeros().intValue()) {
        waiting.add(item.name);
      }
      lines.add(
          new DocumentRequest.Line(
              item.id, line.quantity(), line.batch(), line.expiresOn(), codes));
    }
    var warehouse =
        request.warehouseId() == null
            ? catalog.defaultWarehouse(clinicId())
            : catalog.warehouse(request.warehouseId());
    var draft =
        stock.createDraft(
            new DocumentRequest(
                StockDocument.RECEIPT,
                request.documentDate(),
                request.supplier(),
                null,
                warehouse.id,
                null,
                request.note(),
                request.idempotencyKey(),
                lines));
    if (!waiting.isEmpty() && !StockDocument.POSTED.equals(draft.status)) {
      return new ReceiptView(stock.view(draft, List.of()), false, created.size(), waiting);
    }
    var posted = stock.postDocument(draft, false);
    return new ReceiptView(posted, true, created.size(), List.of());
  }

  /** Позиция строки: по id, по GTIN или названию; новую — только с правом вести номенклатуру. */
  private Item resolve(ReceiptRequest.Line line, HashMap<String, Item> created) {
    if (line.itemId() != null) return catalog.item(line.itemId());
    if (line.name() == null || line.name().isBlank()) {
      throw conflict("RECEIPT_LINE_ITEM_REQUIRED", "Each line needs an item or a name");
    }
    var key = line.name().trim().toLowerCase(Locale.ROOT);
    var known = created.get(key);
    if (known != null) return known;
    var found = catalog.findItem(line.gtin(), line.name());
    if (found.isPresent()) return found.get();
    requirePermission(Permission.INVENTORY_CATALOG_WRITE);
    var item = catalog.createPlainItem(line.name(), line.kind(), line.unit(), line.gtin());
    created.put(key, item);
    return item;
  }
}
