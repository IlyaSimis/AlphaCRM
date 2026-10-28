package ru.quietclinic.inventory;

import static ru.quietclinic.platform.PlatformConfig.conflict;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Разбор кода DataMatrix в формате GS1 — так его отдаёт сканер, работающий как клавиатура. AI 01 —
 * GTIN (14 цифр), AI 21 — серийный номер, AI 17 — срок годности (ГГММДД), AI 10 — партия. Поля
 * переменной длины заканчиваются разделителем GS (0x1D); сканеры без GS дают запись со скобками
 * «(01)…(21)…». Криптохвост (AI 91, 92, 93) отбрасывается и не хранится.
 */
final class Gs1 {
  static final char GS = '\u001d';
  private static final DateTimeFormatter YYMMDD = DateTimeFormatter.ofPattern("yyMMdd");

  /** Разобранная марка: SGTIN = GTIN + серийный номер. */
  record Parsed(String gtin, String serial, String batch, LocalDate expiresOn) {
    String sgtin() {
      return gtin + serial;
    }
  }

  private Gs1() {}

  static Parsed parse(String raw) {
    if (raw == null || raw.isBlank()) throw invalid();
    var text = raw.strip();
    // Префиксы символики сканера: ]d2 (DataMatrix GS1), ]C1; ведущий FNC1.
    if (text.startsWith("]d2") || text.startsWith("]C1") || text.startsWith("]Q3")) {
      text = text.substring(3);
    }
    while (!text.isEmpty() && text.charAt(0) == GS) text = text.substring(1);
    var fields = text.startsWith("(") ? bracketed(text) : raw(text);
    var gtin = fields.get("01");
    var serial = fields.get("21");
    if (gtin == null || !gtin.matches("\\d{14}") || serial == null || serial.isEmpty()) {
      throw invalid();
    }
    if (serial.length() > 20) throw invalid();
    LocalDate expires = null;
    if (fields.containsKey("17")) {
      expires = expiry(fields.get("17"));
    }
    var batch = fields.getOrDefault("10", "");
    return new Parsed(gtin, serial, batch.length() > 40 ? batch.substring(0, 40) : batch, expires);
  }

  /** Срок ГГММДД; день 00 по GS1 означает последний день месяца. */
  private static LocalDate expiry(String value) {
    if (value == null || !value.matches("\\d{6}")) throw invalid();
    try {
      if (value.endsWith("00")) {
        var month = LocalDate.parse(value.substring(0, 4) + "01", YYMMDD);
        return month.withDayOfMonth(month.lengthOfMonth());
      }
      return LocalDate.parse(value, YYMMDD);
    } catch (DateTimeParseException ex) {
      throw invalid();
    }
  }

  private static Map<String, String> bracketed(String text) {
    var result = new LinkedHashMap<String, String>();
    var i = 0;
    while (i < text.length()) {
      if (text.charAt(i) != '(') throw invalid();
      var close = text.indexOf(')', i);
      if (close < 0) throw invalid();
      var ai = text.substring(i + 1, close);
      var next = text.indexOf('(', close);
      var value = next < 0 ? text.substring(close + 1) : text.substring(close + 1, next);
      result.put(ai, value.replace(String.valueOf(GS), ""));
      i = next < 0 ? text.length() : next;
    }
    return result;
  }

  private static Map<String, String> raw(String text) {
    var result = new LinkedHashMap<String, String>();
    var i = 0;
    while (i < text.length()) {
      if (text.charAt(i) == GS) {
        i++;
        continue;
      }
      if (i + 2 > text.length()) throw invalid();
      var ai = text.substring(i, i + 2);
      i += 2;
      int fixed =
          switch (ai) {
            case "01", "02" -> 14;
            case "17", "11", "15" -> 6;
            default -> -1;
          };
      String value;
      if (fixed > 0) {
        if (i + fixed > text.length()) throw invalid();
        value = text.substring(i, i + fixed);
        i += fixed;
      } else if (ai.equals("21") || ai.equals("10") || ai.startsWith("9")) {
        var end = text.indexOf(GS, i);
        // Без разделителя серийный номер маркировки — 13 символов (лекарства) — или до конца.
        if (end < 0 && ai.equals("21") && text.length() - i > 13 && text.startsWith("91", i + 13)) {
          end = i + 13;
        }
        if (end < 0) end = text.length();
        value = text.substring(i, end);
        i = end;
      } else {
        throw invalid();
      }
      result.putIfAbsent(ai, value);
    }
    return result;
  }

  private static RuntimeException invalid() {
    return conflict("INVALID_MARKING_CODE", "The marking code could not be read");
  }
}
