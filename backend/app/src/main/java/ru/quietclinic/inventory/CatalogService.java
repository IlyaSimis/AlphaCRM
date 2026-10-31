package ru.quietclinic.inventory;

import static ru.quietclinic.platform.PlatformConfig.actorId;
import static ru.quietclinic.platform.PlatformConfig.clinicId;
import static ru.quietclinic.platform.PlatformConfig.conflict;
import static ru.quietclinic.platform.PlatformConfig.notFound;
import static ru.quietclinic.platform.PlatformConfig.requirePermission;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.quietclinic.audit.AuditService;
import ru.quietclinic.platform.Permission;

/** Номенклатура, категории и склады клиники. */
@Service
class CatalogService {
  private final ItemCategoryRepository categories;
  private final ItemRepository items;
  private final WarehouseRepository warehouses;
  private final JdbcTemplate jdbc;
  private final AuditService audit;

  /** Название основного склада — данные, поэтому в конфигурации, а не в коде. */
  private final String defaultWarehouseName;

  /** Названия основных категорий по видам и единица по умолчанию — тоже данные. */
  private final Map<String, String> defaultCategoryNames;

  private final String defaultUnit;

  CatalogService(
      ItemCategoryRepository categories,
      ItemRepository items,
      WarehouseRepository warehouses,
      JdbcTemplate jdbc,
      AuditService audit,
      @org.springframework.beans.factory.annotation.Value(
              "${clinic.inventory.default-warehouse-name:Main warehouse}")
          String defaultWarehouseName,
      @org.springframework.beans.factory.annotation.Value(
              "${clinic.inventory.default-category.drug:Drugs}")
          String drugs,
      @org.springframework.beans.factory.annotation.Value(
              "${clinic.inventory.default-category.medical-device:Medical devices}")
          String devices,
      @org.springframework.beans.factory.annotation.Value(
              "${clinic.inventory.default-category.consumable:Consumables}")
          String consumables,
      @org.springframework.beans.factory.annotation.Value(
              "${clinic.inventory.default-category.goods:Goods}")
          String goods,
      @org.springframework.beans.factory.annotation.Value("${clinic.inventory.default-unit:pcs}")
          String defaultUnit) {
    this.defaultWarehouseName = defaultWarehouseName;
    this.defaultCategoryNames =
        Map.of("DRUG", drugs, "MEDICAL_DEVICE", devices, "CONSUMABLE", consumables, "GOODS", goods);
    this.defaultUnit = defaultUnit;
    this.categories = categories;
    this.items = items;
    this.warehouses = warehouses;
    this.jdbc = jdbc;
    this.audit = audit;
  }

  @Transactional(readOnly = true)
  List<CategoryView> categories() {
    requirePermission(Permission.INVENTORY_CATALOG_READ, Permission.INVENTORY_STOCK_READ);
    return categories.findByClinicIdOrderByName(clinicId()).stream().map(CategoryView::of).toList();
  }

  @Transactional
  CategoryView createCategory(CategoryRequest request) {
    requirePermission(Permission.INVENTORY_CATALOG_WRITE);
    var name = request.name().trim();
    if (request.parentId() != null) category(request.parentId());
    if (categories.existsByClinicIdAndNameIgnoreCaseAndParentId(
        clinicId(), name, request.parentId())) {
      throw conflict("CATEGORY_NAME_TAKEN", "A category with this name already exists");
    }
    var category =
        categories.save(new ItemCategory(clinicId(), name, request.parentId(), request.kind()));
    audit.record(actorId(), "ITEM_CATEGORY_CREATED", "ITEM_CATEGORY", category.id, "ALLOWED", null);
    return CategoryView.of(category);
  }

  @Transactional
  CategoryView updateCategory(UUID id, CategoryRequest request) {
    requirePermission(Permission.INVENTORY_CATALOG_WRITE);
    var category = category(id);
    if (id.equals(request.parentId())) {
      throw conflict("INVALID_PARENT", "A category cannot be its own parent");
    }
    if (request.parentId() != null) category(request.parentId());
    category.name = request.name().trim();
    category.parentId = request.parentId();
    category.kind = request.kind();
    audit.record(actorId(), "ITEM_CATEGORY_UPDATED", "ITEM_CATEGORY", category.id, "ALLOWED", null);
    return CategoryView.of(category);
  }

  /**
   * Номенклатура с фильтрами: категория (вместе с подкатегориями), вид, маркируемость, поиск по
   * названию и GTIN; сортировка name, -name, category, stock, -stock.
   */
  @Transactional(readOnly = true)
  List<ItemView> items(UUID categoryId, String kind, Boolean marked, String query, String sort) {
    requirePermission(Permission.INVENTORY_CATALOG_READ, Permission.INVENTORY_STOCK_READ);
    var allCategories = categories.findByClinicIdOrderByName(clinicId());
    var byId = new HashMap<UUID, ItemCategory>();
    allCategories.forEach(c -> byId.put(c.id, c));
    var stock = stockByItem();
    var needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
    var rows =
        items.findByClinicIdOrderByName(clinicId()).stream()
            .filter(i -> categoryId == null || within(i.categoryId, categoryId, byId))
            .filter(i -> kind == null || kind.isBlank() || kind.equals(kindOf(i, byId)))
            .filter(i -> marked == null || i.marked == marked)
            .filter(
                i ->
                    needle.isEmpty()
                        || i.name.toLowerCase(Locale.ROOT).contains(needle)
                        || (i.gtin != null && i.gtin.contains(needle)))
            .map(i -> view(i, byId, stock))
            .toList();
    Comparator<ItemView> order =
        switch (sort == null ? "name" : sort) {
          case "-name" ->
              Comparator.comparing(ItemView::name, String.CASE_INSENSITIVE_ORDER).reversed();
          case "category" ->
              Comparator.comparing(ItemView::categoryName, String.CASE_INSENSITIVE_ORDER)
                  .thenComparing(ItemView::name, String.CASE_INSENSITIVE_ORDER);
          case "stock" -> Comparator.comparing(ItemView::stock);
          case "-stock" -> Comparator.comparing(ItemView::stock).reversed();
          default -> Comparator.comparing(ItemView::name, String.CASE_INSENSITIVE_ORDER);
        };
    return rows.stream().sorted(order).toList();
  }

  @Transactional
  ItemView createItem(ItemRequest request) {
    requirePermission(Permission.INVENTORY_CATALOG_WRITE);
    var categoryId = categoryOf(request.categoryId(), request.kind());
    var item = new Item(clinicId(), categoryId, request.name().trim(), request.unit().trim());
    apply(item, request);
    items.save(item);
    audit.record(actorId(), "ITEM_CREATED", "ITEM", item.id, "ALLOWED", null);
    return view(item);
  }

  @Transactional
  ItemView updateItem(UUID id, ItemRequest request) {
    requirePermission(Permission.INVENTORY_CATALOG_WRITE);
    var item = item(id);
    item.categoryId =
        request.categoryId() == null && request.kind() == null
            ? item.categoryId
            : categoryOf(request.categoryId(), request.kind());
    item.name = request.name().trim();
    item.unit = request.unit().trim();
    apply(item, request);
    if (request.active() != null) item.active = request.active();
    audit.record(actorId(), "ITEM_UPDATED", "ITEM", item.id, "ALLOWED", null);
    return view(item);
  }

  private static void apply(Item item, ItemRequest request) {
    var marked = Boolean.TRUE.equals(request.marked());
    var system = request.markingSystem() == null ? "NONE" : request.markingSystem();
    if (marked && ("NONE".equals(system) || request.gtin() == null)) {
      throw conflict("MARKING_REQUIRES_GTIN", "A marked item needs a GTIN and a marking system");
    }
    item.gtin = request.gtin() == null || request.gtin().isBlank() ? null : request.gtin();
    item.marked = marked;
    item.markingSystem = marked ? system : "NONE";
    item.rxOnly = Boolean.TRUE.equals(request.rxOnly());
    if (request.minStock() != null) item.minStock = request.minStock();
  }

  @Transactional
  List<WarehouseView> warehouses() {
    requirePermission(
        Permission.INVENTORY_STOCK_READ,
        Permission.INVENTORY_CATALOG_READ,
        Permission.INVENTORY_DOCUMENT_WRITE);
    defaultWarehouse(clinicId());
    return warehouses.findByClinicIdOrderByName(clinicId()).stream()
        .map(WarehouseView::of)
        .toList();
  }

  @Transactional
  WarehouseView createWarehouse(WarehouseRequest request) {
    requirePermission(Permission.INVENTORY_CATALOG_WRITE);
    defaultWarehouse(clinicId());
    var warehouse = warehouses.save(new Warehouse(clinicId(), request.name().trim(), false));
    audit.record(actorId(), "WAREHOUSE_CREATED", "WAREHOUSE", warehouse.id, "ALLOWED", null);
    return WarehouseView.of(warehouse);
  }

  /** Выбранная категория или основная категория вида (без вида — расходники). */
  UUID categoryOf(UUID categoryId, String kind) {
    if (categoryId != null) return category(categoryId).id;
    return defaultCategory(kind == null ? "CONSUMABLE" : kind).id;
  }

  /**
   * Основная категория вида: верхнего уровня, с названием из конфигурации; создаётся при первой
   * позиции без категории. Так позицию можно завести, не настраивая категории заранее.
   */
  ItemCategory defaultCategory(String kind) {
    var name = defaultCategoryNames.getOrDefault(kind, kind);
    return categories.findByClinicIdOrderByName(clinicId()).stream()
        .filter(c -> c.parentId == null && kind.equals(c.kind) && c.name.equalsIgnoreCase(name))
        .findFirst()
        .orElseGet(
            () -> {
              var category =
                  categories.saveAndFlush(new ItemCategory(clinicId(), name, null, kind));
              audit.record(
                  actorId(),
                  "ITEM_CATEGORY_CREATED",
                  "ITEM_CATEGORY",
                  category.id,
                  "ALLOWED",
                  null);
              return category;
            });
  }

  /** Активная позиция с этим GTIN или названием (без учёта регистра); для быстрого прихода. */
  java.util.Optional<Item> findItem(String gtin, String name) {
    if (gtin != null && !gtin.isBlank()) {
      var byGtin = items.findByClinicIdAndGtin(clinicId(), gtin);
      if (!byGtin.isEmpty()) return java.util.Optional.of(byGtin.get(0));
    }
    if (name == null || name.isBlank()) return java.util.Optional.empty();
    var needle = name.trim();
    return items.findByClinicIdOrderByName(clinicId()).stream()
        .filter(i -> i.name.equalsIgnoreCase(needle))
        .sorted(Comparator.comparing((Item i) -> !i.active))
        .findFirst();
  }

  /** Новая немаркируемая позиция в основной категории вида — для строки быстрого прихода. */
  Item createPlainItem(String name, String kind, String unit, String gtin) {
    var item =
        new Item(
            clinicId(),
            defaultCategory(kind == null ? "CONSUMABLE" : kind).id,
            name.trim(),
            unit == null || unit.isBlank() ? defaultUnit : unit.trim());
    item.gtin = gtin == null || gtin.isBlank() ? null : gtin;
    items.saveAndFlush(item);
    audit.record(actorId(), "ITEM_CREATED", "ITEM", item.id, "ALLOWED", "receipt");
    return item;
  }

  /** Основной склад клиники; создаётся при первом обращении. */
  Warehouse defaultWarehouse(UUID clinicId) {
    var all = warehouses.findByClinicIdOrderByName(clinicId);
    return all.stream()
        .filter(w -> w.defaultWarehouse)
        .findFirst()
        .orElseGet(
            () ->
                all.isEmpty()
                    ? warehouses.saveAndFlush(new Warehouse(clinicId, defaultWarehouseName, true))
                    : all.get(0));
  }

  ItemCategory category(UUID id) {
    return categories
        .findByIdAndClinicId(id, clinicId())
        .orElseThrow(() -> notFound("ITEM_CATEGORY_NOT_FOUND", "Item category not found"));
  }

  Item item(UUID id) {
    return items
        .findByIdAndClinicId(id, clinicId())
        .orElseThrow(() -> notFound("ITEM_NOT_FOUND", "Item not found"));
  }

  Warehouse warehouse(UUID id) {
    return warehouses
        .findByIdAndClinicId(id, clinicId())
        .orElseThrow(() -> notFound("WAREHOUSE_NOT_FOUND", "Warehouse not found"));
  }

  ItemView view(Item item) {
    var byId = new HashMap<UUID, ItemCategory>();
    categories.findByClinicIdOrderByName(clinicId()).forEach(c -> byId.put(c.id, c));
    return view(item, byId, stockByItem());
  }

  private static ItemView view(
      Item i, Map<UUID, ItemCategory> categories, Map<UUID, BigDecimal> stock) {
    var category = categories.get(i.categoryId);
    return new ItemView(
        i.id,
        i.categoryId,
        category == null ? "" : category.name,
        kindOf(i, categories),
        i.name,
        i.unit,
        i.gtin,
        i.marked,
        i.markingSystem,
        i.rxOnly,
        i.active,
        stock.getOrDefault(i.id, BigDecimal.ZERO),
        i.minStock);
  }

  private static String kindOf(Item item, Map<UUID, ItemCategory> categories) {
    var category = categories.get(item.categoryId);
    return category == null ? null : category.kind;
  }

  /** Категория равна искомой или вложена в неё. */
  private static boolean within(UUID categoryId, UUID wanted, Map<UUID, ItemCategory> categories) {
    var current = categoryId;
    for (var depth = 0; current != null && depth < 20; depth++) {
      if (current.equals(wanted)) return true;
      var category = categories.get(current);
      current = category == null ? null : category.parentId;
    }
    return false;
  }

  private Map<UUID, BigDecimal> stockByItem() {
    var result = new HashMap<UUID, BigDecimal>();
    jdbc.query(
        "select item_id, sum(quantity) as total from inventory.stock_balance where clinic_id = ?"
            + " group by item_id",
        rs -> {
          result.put(rs.getObject("item_id", UUID.class), rs.getBigDecimal("total"));
        },
        clinicId());
    return result;
  }
}
