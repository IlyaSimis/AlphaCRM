package ru.quietclinic.inventory;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import ru.quietclinic.platform.DomainEvent;
import ru.quietclinic.platform.DomainEventHandler;

/**
 * Потребитель {@code AppointmentCompleted} (сценарий D7): расход по нормам услуги с основного
 * склада. Немаркируемые позиции проводятся сразу; нехватка остатка не блокирует приём — расход
 * уходит в минус и публикуется {@code StockShortage}. Маркируемые позиции ждут сканирования марок:
 * по ним создаётся черновик расхода. В документе — только id приёма, без пациента и диагноза.
 */
@Component
class ConsumptionOnCompletedVisit implements DomainEventHandler {
  private final StockService stock;
  private final ItemRepository items;

  ConsumptionOnCompletedVisit(StockService stock, ItemRepository items) {
    this.stock = stock;
    this.items = items;
  }

  @Override
  public String consumer() {
    return "inventory.consumption-on-completed";
  }

  @Override
  public Set<String> types() {
    return Set.of("AppointmentCompleted");
  }

  @Override
  public void handle(DomainEvent event) {
    List<Map<String, Object>> norms = event.list("consumption");
    if (norms.isEmpty()) return;
    UUID appointmentId = event.uuid("appointmentId");
    List<DocumentRequest.Line> plain = new ArrayList<>();
    List<DocumentRequest.Line> marked = new ArrayList<>();
    for (Map<String, Object> norm : norms) {
      UUID itemId = UUID.fromString(norm.get("itemId").toString());
      Optional<Item> item =
          items.findByIdAndClinicId(itemId, event.clinicId()).filter(i -> i.active);
      if (item.isEmpty()) continue; // позицию удалили из номенклатуры — норму пропускаем
      DocumentRequest.Line line =
          new DocumentRequest.Line(
              itemId, new BigDecimal(norm.get("quantity").toString()), null, null, List.of());
      (item.get().marked ? marked : plain).add(line);
    }
    if (!plain.isEmpty()) {
      StockDocument document = stock.createDraft(request(appointmentId, "auto", plain));
      stock.postDocument(document, true);
    }
    if (!marked.isEmpty()) {
      stock.createDraft(request(appointmentId, "scan", marked));
    }
  }

  private static DocumentRequest request(
      UUID appointmentId, String suffix, List<DocumentRequest.Line> lines) {
    return new DocumentRequest(
        "CONSUMPTION",
        null,
        null,
        null,
        null,
        appointmentId,
        null,
        "appointment:" + appointmentId + ":" + suffix,
        lines);
  }
}
