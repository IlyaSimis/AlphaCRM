package ru.quietclinic.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import ru.quietclinic.SecondClinic;
import ru.quietclinic.StaffSessions;

/**
 * Склад (сценарии D6 и D7): номенклатура, приход с марками, приёмка через mock-адаптер маркировки,
 * расход с выводом из оборота, автосписание по нормам услуги и нехватка остатка.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class InventoryControllerTest {
  static final String ANNA = "00000000-0000-0000-0000-000000000101";

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  StaffSessions staff;

  @BeforeEach
  void signIn() {
    staff = new StaffSessions(mvc);
  }

  private String send(ResultActions actions, String path) throws Exception {
    return JsonPath.read(actions.andReturn().getResponse().getContentAsString(), path).toString();
  }

  private ResultActions postJson(String url, RequestPostProcessor who, String body)
      throws Exception {
    return mvc.perform(post(url).with(who).contentType(MediaType.APPLICATION_JSON).content(body));
  }

  private String category(String kind) throws Exception {
    return send(
        postJson(
                "/api/v1/inventory/categories",
                staff.role("NURSE"),
                "{\"name\":\"Cat %s\",\"kind\":\"%s\"}".formatted(UUID.randomUUID(), kind))
            .andExpect(status().isCreated()),
        "$.id");
  }

  private String item(String category, String body) throws Exception {
    return send(
        postJson(
                "/api/v1/inventory/items",
                staff.role("NURSE"),
                body.formatted(category, UUID.randomUUID().toString().substring(0, 6)))
            .andExpect(status().isCreated()),
        "$.id");
  }

  private ResultActions document(RequestPostProcessor who, String type, String lines)
      throws Exception {
    return postJson(
        "/api/v1/inventory/documents",
        who,
        "{\"type\":\"%s\",\"idempotencyKey\":\"%s\",\"lines\":%s}"
            .formatted(type, UUID.randomUUID(), lines));
  }

  /** Сумма остатков позиции по всем складам и партиям (число из JSON — без привязки к типу). */
  private java.math.BigDecimal stock(RequestPostProcessor who, String itemId) throws Exception {
    var body =
        mvc.perform(get("/api/v1/inventory/balances").with(who))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    List<Object> values = JsonPath.read(body, "$[?(@.itemId == '" + itemId + "')].quantity");
    return values.stream()
        .map(value -> new java.math.BigDecimal(value.toString()))
        .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
  }

  private ResultActions postDoc(RequestPostProcessor who, String documentId, String body)
      throws Exception {
    return postJson("/api/v1/inventory/documents/" + documentId + "/post", who, body);
  }

  @Test
  void receiptAndConsumptionMoveStockAndNeverGoNegativeWithoutPermission() throws Exception {
    var gloves =
        item(
            category("CONSUMABLE"),
            "{\"categoryId\":\"%s\",\"name\":\"Gloves %s\",\"unit\":\"pair\"}");
    var receipt =
        send(
            document(
                    staff.role("NURSE"),
                    "RECEIPT",
                    "[{\"itemId\":\"%s\",\"quantity\":10,\"batch\":\"B1\"}]".formatted(gloves))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT")),
            "$.id");
    postDoc(staff.role("NURSE"), receipt, "{}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("POSTED"));
    // Повтор проведения безопасен; проведённый документ не отменить.
    postDoc(staff.role("NURSE"), receipt, "{}").andExpect(status().isOk());
    postJson("/api/v1/inventory/documents/" + receipt + "/cancel", staff.role("NURSE"), "")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("DOCUMENT_POSTED"));

    assertThat(stock(staff.role("DOCTOR"), gloves)).isEqualByComparingTo("10");

    var tooMuch =
        send(
            document(
                staff.role("NURSE"),
                "CONSUMPTION",
                "[{\"itemId\":\"%s\",\"quantity\":12}]".formatted(gloves)),
            "$.id");
    postDoc(staff.role("NURSE"), tooMuch, "{}")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));
    postDoc(staff.role("NURSE"), tooMuch, "{\"allowNegative\":true}")
        .andExpect(status().isForbidden());
    // Администратор проводит расход сверх остатка: нехватка — событие и задача регистратуры.
    postDoc(staff.admin(), tooMuch, "{\"allowNegative\":true}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.shortages[0].itemId").value(gloves));
    assertThat(stock(staff.admin(), gloves)).isEqualByComparingTo("-2");
    var tasks =
        jdbc.queryForObject(
            "select count(*) from registry_task where title like ?", Long.class, "%Gloves%");
    assertThat(tasks).isGreaterThanOrEqualTo(1L);
  }

  @Test
  void markedDrugIsAcceptedThenWithdrawnPackageByPackage() throws Exception {
    var gtin = "0460" + String.valueOf(System.nanoTime()).substring(0, 10);
    var drug =
        item(
            category("DRUG"),
            "{\"categoryId\":\"%s\",\"name\":\"Drug %s\",\"unit\":\"pack\",\"gtin\":\""
                + gtin
                + "\",\"marked\":true,\"markingSystem\":\"MDLP\"}");
    var first =
        "01" + gtin + "21SER" + UUID.randomUUID().toString().substring(0, 8) + "\u001d91EE07";
    var second =
        "(01)"
            + gtin
            + "(21)SER"
            + UUID.randomUUID().toString().substring(0, 8)
            + "(17)271231(10)LOT1";

    // Марок должно быть столько же, сколько упаковок.
    var mismatch =
        send(
            document(
                staff.role("NURSE"),
                "RECEIPT",
                "[{\"itemId\":\"%s\",\"quantity\":3,\"codes\":[\"%s\"]}]"
                    .formatted(drug, first.replace("\u001d", "\\u001d"))),
            "$.id");
    postDoc(staff.role("NURSE"), mismatch, "{}")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("MARKING_CODES_MISMATCH"));

    var receipt =
        send(
            document(
                staff.role("NURSE"),
                "RECEIPT",
                "[{\"itemId\":\"%s\",\"quantity\":2,\"codes\":[\"%s\",\"%s\"]}]"
                    .formatted(drug, first.replace("\u001d", "\\u001d"), second)),
            "$.id");
    postDoc(staff.role("NURSE"), receipt, "{}").andExpect(status().isOk());

    // Mock-адаптер (профиль local) принял приёмку: марки в наличии.
    var codes =
        mvc.perform(get("/api/v1/inventory/marking/codes").param("item", drug).with(staff.admin()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    List<String> statuses = JsonPath.read(codes, "$[*].status");
    assertThat(statuses).containsOnly("IN_STOCK").hasSize(2);
    List<String> sgtins = JsonPath.read(codes, "$[*].sgtin");
    mvc.perform(get("/api/v1/inventory/marking/operations").with(staff.role("SUPPORT")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.documentId == '" + receipt + "')].status").value("DONE"));

    var consumption =
        send(
            document(
                staff.role("NURSE"),
                "CONSUMPTION",
                "[{\"itemId\":\"%s\",\"quantity\":1,\"codes\":[\"%s\"]}]"
                    .formatted(drug, sgtins.get(0))),
            "$.id");
    postDoc(staff.role("NURSE"), consumption, "{}").andExpect(status().isOk());
    mvc.perform(get("/api/v1/inventory/marking/codes").param("item", drug).with(staff.admin()))
        .andExpect(jsonPath("$[?(@.sgtin == '" + sgtins.get(0) + "')].status").value("WITHDRAWN"));

    // Выбывшую упаковку повторно не списать.
    var again =
        send(
            document(
                staff.role("NURSE"),
                "CONSUMPTION",
                "[{\"itemId\":\"%s\",\"quantity\":1,\"codes\":[\"%s\"]}]"
                    .formatted(drug, sgtins.get(0))),
            "$.id");
    postDoc(staff.role("NURSE"), again, "{}")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("MARKING_CODE_NOT_IN_STOCK"));
  }

  @Test
  void completedVisitConsumesServiceNorms() throws Exception {
    var syringes =
        item(
            category("CONSUMABLE"),
            "{\"categoryId\":\"%s\",\"name\":\"Syringe %s\",\"unit\":\"pc\"}");
    var receipt =
        send(
            document(
                staff.role("NURSE"),
                "RECEIPT",
                "[{\"itemId\":\"%s\",\"quantity\":5}]".formatted(syringes)),
            "$.id");
    postDoc(staff.role("NURSE"), receipt, "{}").andExpect(status().isOk());

    var name = "Injection " + UUID.randomUUID().toString().substring(0, 8);
    var service =
        send(
            postJson(
                    "/api/v1/services",
                    staff.admin(),
                    "{\"name\":\"%s\",\"durationMinutes\":30,\"priceMinor\":100000,\"currency\":\"RUB\"}"
                        .formatted(name))
                .andExpect(status().isCreated()),
            "$.id");
    mvc.perform(
            put("/api/v1/services/" + service + "/consumption-norms")
                .with(staff.role("CASHIER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"norms\":[{\"itemId\":\"%s\",\"quantity\":2}]}".formatted(syringes)))
        .andExpect(status().isForbidden());
    mvc.perform(
            put("/api/v1/services/" + service + "/consumption-norms")
                .with(staff.role("NURSE"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"norms\":[{\"itemId\":\"%s\",\"quantity\":2}]}".formatted(syringes)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].itemId").value(syringes));

    var appointment =
        send(
            postJson(
                    "/api/v1/appointments",
                    staff.admin(),
                    """
                    {"patientId":"%s","practitionerId":"%s","startsAt":"2027-04-13T14:00:00Z",
                     "endsAt":"2027-04-13T14:30:00Z","serviceName":"%s"}
                    """
                        .formatted(ANNA, StaffSessions.PETROV, name))
                .andExpect(status().isCreated()),
            "$.id");
    postJson(
            "/api/v1/appointments/" + appointment + "/status",
            staff.admin(),
            "{\"status\":\"ARRIVED\"}")
        .andExpect(status().isOk());
    postJson(
            "/api/v1/appointments/" + appointment + "/status",
            staff.doctor(StaffSessions.PETROV),
            "{\"status\":\"COMPLETED\"}")
        .andExpect(status().isOk());

    assertThat(stock(staff.role("NURSE"), syringes)).isEqualByComparingTo("3");
    // Расход хранит id приёма — без пациента; врачу накладные не открываются.
    mvc.perform(
            get("/api/v1/inventory/documents")
                .param("type", "CONSUMPTION")
                .with(staff.role("NURSE")))
        .andExpect(jsonPath("$[*].appointmentId").value(hasItem(appointment)))
        .andExpect(jsonPath("$[*].patientId").doesNotExist());
    mvc.perform(get("/api/v1/inventory/documents").with(staff.role("DOCTOR")))
        .andExpect(status().isForbidden());
  }

  @Test
  void scanReadsCodeAndOtherClinicDoesNotSeeItems() throws Exception {
    var gloves =
        item(
            category("CONSUMABLE"),
            "{\"categoryId\":\"%s\",\"name\":\"Private %s\",\"unit\":\"pc\"}");
    postJson(
            "/api/v1/inventory/marking/scan",
            staff.role("NURSE"),
            "{\"code\":\"(01)04601234567893(21)ABC(17)271231\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.gtin").value("04601234567893"))
        .andExpect(jsonPath("$.expiresOn").value("2027-12-31"));
    postJson("/api/v1/inventory/marking/scan", staff.role("NURSE"), "{\"code\":\"garbage\"}")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("INVALID_MARKING_CODE"));

    SecondClinic.ensure(jdbc);
    var other = staff.login(SecondClinic.ADMIN_USERNAME, SecondClinic.ADMIN_PASSWORD);
    mvc.perform(get("/api/v1/inventory/items").with(other))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[*].id").value(not(hasItem(gloves))));
    mvc.perform(
            put("/api/v1/inventory/items/" + gloves)
                .with(other)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"categoryId\":\"%s\",\"name\":\"x\",\"unit\":\"pc\"}"
                        .formatted(UUID.randomUUID())))
        .andExpect(status().isNotFound());
    mvc.perform(get("/api/v1/inventory/items").with(staff.role("CASHIER")))
        .andExpect(status().isForbidden());
  }

  @Test
  void quickReceiptCreatesMissingItemsPostsAndReusesThemByName() throws Exception {
    var name = "Бинт " + UUID.randomUUID().toString().substring(0, 6);
    var body =
        """
        {"idempotencyKey":"%s","supplier":"Аптека","lines":[
          {"name":"%s","kind":"CONSUMABLE","unit":"шт","quantity":10,"batch":"B1","expiresOn":"2030-01-01"},
          {"name":"%s","quantity":2}
        ]}\
        """
            .formatted(UUID.randomUUID(), name, name.toUpperCase());
    var created =
        postJson("/api/v1/inventory/receipts", staff.role("NURSE"), body)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.posted").value(true))
            .andExpect(jsonPath("$.itemsCreated").value(1))
            .andExpect(jsonPath("$.document.status").value("POSTED"))
            .andExpect(jsonPath("$.document.type").value("RECEIPT"));
    var itemId = send(created, "$.document.lines[0].itemId");
    assertThat(send(created, "$.document.lines[1].itemId")).isEqualTo(itemId);
    assertThat(stock(staff.role("NURSE"), itemId)).isEqualByComparingTo("12");

    // Второй приход по названию — та же позиция, новая не заводится; категория — основная.
    postJson(
            "/api/v1/inventory/receipts",
            staff.role("NURSE"),
            "{\"idempotencyKey\":\"%s\",\"lines\":[{\"name\":\"%s\",\"quantity\":3}]}"
                .formatted(UUID.randomUUID(), name))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.itemsCreated").value(0));
    assertThat(stock(staff.role("NURSE"), itemId)).isEqualByComparingTo("15");
    mvc.perform(get("/api/v1/inventory/items?q=" + name.substring(5)).with(staff.role("NURSE")))
        .andExpect(jsonPath("$[0].categoryName").value("Расходники"))
        .andExpect(jsonPath("$[0].unit").value("шт"));
  }

  @Test
  void itemWithoutCategoryGoesToTheDefaultCategoryOfItsKind() throws Exception {
    postJson(
            "/api/v1/inventory/items",
            staff.role("NURSE"),
            "{\"name\":\"Аспирин %s\",\"unit\":\"уп\",\"kind\":\"DRUG\"}"
                .formatted(UUID.randomUUID().toString().substring(0, 6)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.categoryName").value("Лекарства"))
        .andExpect(jsonPath("$.kind").value("DRUG"));
  }

  @Test
  void quickReceiptOfMarkedItemWithoutCodesStaysDraftAndCashierCannotReceive() throws Exception {
    var drug =
        item(
            category("DRUG"),
            "{\"categoryId\":\"%s\",\"name\":\"Марк"
                + " %s\",\"unit\":\"уп\",\"gtin\":\"04601234567893\",\"marked\":true,\"markingSystem\":\"MDLP\"}");
    postJson(
            "/api/v1/inventory/receipts",
            staff.role("NURSE"),
            "{\"idempotencyKey\":\"%s\",\"lines\":[{\"itemId\":\"%s\",\"quantity\":2}]}"
                .formatted(UUID.randomUUID(), drug))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.posted").value(false))
        .andExpect(jsonPath("$.document.status").value("DRAFT"))
        .andExpect(jsonPath("$.waitingCodes[0]").exists());
    postJson(
            "/api/v1/inventory/receipts",
            staff.role("CASHIER"),
            "{\"idempotencyKey\":\"%s\",\"lines\":[{\"name\":\"x\",\"quantity\":1}]}"
                .formatted(UUID.randomUUID()))
        .andExpect(status().isForbidden());
  }
}
