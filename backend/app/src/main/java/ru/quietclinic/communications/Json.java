package ru.quietclinic.communications;

import java.util.Map;

/** Минимальная запись JSON для тела вебхука и Telegram: строки и плоские объекты. */
final class Json {
  private Json() {}

  static String quote(String value) {
    if (value == null) return "null";
    var out = new StringBuilder("\"");
    for (var ch : value.toCharArray()) {
      switch (ch) {
        case '"' -> out.append("\\\"");
        case '\\' -> out.append("\\\\");
        case '\n' -> out.append("\\n");
        case '\r' -> out.append("\\r");
        case '\t' -> out.append("\\t");
        default -> {
          if (ch < 0x20) out.append(String.format("\\u%04x", (int) ch));
          else out.append(ch);
        }
      }
    }
    return out.append('"').toString();
  }

  /** Объект из пар «ключ → значение»; значение — строка (в кавычки) или готовый JSON. */
  static String object(Map<String, ?> fields) {
    var out = new StringBuilder("{");
    var first = true;
    for (var entry : fields.entrySet()) {
      if (!first) out.append(',');
      first = false;
      out.append(quote(entry.getKey())).append(':');
      var value = entry.getValue();
      if (value instanceof RawJson raw) out.append(raw.json());
      else out.append(quote(value == null ? null : value.toString()));
    }
    return out.append('}').toString();
  }
}
