package ru.quietclinic.inventory;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Склад: номенклатура, склады, накладные, остатки и маркировка (раздел F, inventory). */
@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryController {
  private final CatalogService catalog;
  private final StockService stock;
  private final MarkingService marking;
  private final ReceiptService receipts;

  InventoryController(
      CatalogService catalog, StockService stock, MarkingService marking, ReceiptService receipts) {
    this.catalog = catalog;
    this.stock = stock;
    this.marking = marking;
    this.receipts = receipts;
  }

  /** Приход в одно действие: новые позиции заводятся, накладная проводится. */
  @PostMapping("/receipts")
  @ResponseStatus(HttpStatus.CREATED)
  ReceiptView receive(@Valid @RequestBody ReceiptRequest request) {
    return receipts.receive(request);
  }

  @GetMapping("/categories")
  List<CategoryView> categories() {
    return catalog.categories();
  }

  @PostMapping("/categories")
  @ResponseStatus(HttpStatus.CREATED)
  CategoryView createCategory(@Valid @RequestBody CategoryRequest request) {
    return catalog.createCategory(request);
  }

  @PutMapping("/categories/{id}")
  CategoryView updateCategory(@PathVariable UUID id, @Valid @RequestBody CategoryRequest request) {
    return catalog.updateCategory(id, request);
  }

  @GetMapping("/items")
  List<ItemView> items(
      @RequestParam(required = false) UUID category,
      @RequestParam(required = false) String kind,
      @RequestParam(required = false) Boolean marked,
      @RequestParam(required = false) String q,
      @RequestParam(required = false) String sort) {
    return catalog.items(category, kind, marked, q, sort);
  }

  @PostMapping("/items")
  @ResponseStatus(HttpStatus.CREATED)
  ItemView createItem(@Valid @RequestBody ItemRequest request) {
    return catalog.createItem(request);
  }

  @PutMapping("/items/{id}")
  ItemView updateItem(@PathVariable UUID id, @Valid @RequestBody ItemRequest request) {
    return catalog.updateItem(id, request);
  }

  @GetMapping("/warehouses")
  List<WarehouseView> warehouses() {
    return catalog.warehouses();
  }

  @PostMapping("/warehouses")
  @ResponseStatus(HttpStatus.CREATED)
  WarehouseView createWarehouse(@Valid @RequestBody WarehouseRequest request) {
    return catalog.createWarehouse(request);
  }

  @GetMapping("/documents")
  List<DocumentView> documents(
      @RequestParam(required = false) String type, @RequestParam(required = false) String status) {
    return stock.list(type, status);
  }

  @GetMapping("/documents/{id}")
  DocumentView document(@PathVariable UUID id) {
    return stock.get(id);
  }

  @PostMapping("/documents")
  @ResponseStatus(HttpStatus.CREATED)
  DocumentView createDocument(@Valid @RequestBody DocumentRequest request) {
    return stock.create(request);
  }

  @PutMapping("/documents/{id}")
  DocumentView updateDocument(@PathVariable UUID id, @Valid @RequestBody DocumentRequest request) {
    return stock.update(id, request);
  }

  @PostMapping("/documents/{id}/post")
  DocumentView post(@PathVariable UUID id, @RequestBody(required = false) PostRequest request) {
    return stock.post(id, request != null && Boolean.TRUE.equals(request.allowNegative()));
  }

  @PostMapping("/documents/{id}/cancel")
  DocumentView cancel(@PathVariable UUID id) {
    return stock.cancel(id);
  }

  @GetMapping("/balances")
  List<BalanceView> balances(
      @RequestParam(required = false) UUID warehouse,
      @RequestParam(required = false) UUID category,
      @RequestParam(required = false) String q,
      @RequestParam(defaultValue = "false") boolean includeZero) {
    return stock.balances(warehouse, category, q, includeZero);
  }

  @GetMapping("/metrics")
  StockMetricsView metrics(@RequestParam java.time.LocalDate from, @RequestParam java.time.LocalDate to) {
    return stock.metrics(from, to);
  }

  @PostMapping("/marking/scan")
  ScanView scan(@Valid @RequestBody ScanRequest request) {
    return marking.scan(request);
  }

  @GetMapping("/marking/codes")
  List<MarkingCodeView> codes(
      @RequestParam(required = false) String status, @RequestParam(required = false) UUID item) {
    return marking.codes(status, item);
  }

  @GetMapping("/marking/operations")
  List<MarkingOperationView> operations(@RequestParam(required = false) String status) {
    return marking.operations(status);
  }

  @PostMapping("/marking/operations/{id}/retry")
  MarkingOperationView retry(@PathVariable UUID id) {
    return marking.retry(id);
  }

  @GetMapping("/marking/status")
  MarkingStatusView status() {
    return marking.status();
  }
}
