package ru.quietclinic.clinical;

import java.util.HashMap;
import java.util.Map;

/** Каноническое содержимое документа из четырёх полей заметки: от него считается хеш подписи. */
final class NoteContent {
  private NoteContent() {}

  /**
   * Поля из подписанного снимка — плоского JSON-объекта со строковыми значениями. Снимок не в этом
   * формате (например, произвольный текст из API) даёт пустые поля, а не ошибку.
   */
  static SnapshotFields parse(String snapshot) {
    var values = parseFlatObject(snapshot);
    if (values == null) return SnapshotFields.EMPTY;
    return new SnapshotFields(
        values.get("complaints"),
        values.get("mentalStatus"),
        values.get("diagnosis"),
        values.get("recommendations"));
  }

  private static Map<String, String> parseFlatObject(String text) {
    if (text == null) return null;
    var reader = new FlatJsonReader(text.trim());
    var result = new HashMap<String, String>();
    if (!reader.consume('{')) return null;
    if (reader.consume('}')) return reader.atEnd() ? result : null;
    do {
      var key = reader.string();
      if (key == null || !reader.consume(':')) return null;
      var value = reader.string();
      if (value == null) return null;
      result.put(key, value);
    } while (reader.consume(','));
    return reader.consume('}') && reader.atEnd() ? result : null;
  }

  static String canonical(
      String complaints, String mentalStatus, String diagnosis, String recommendations) {
    return "{\"complaints\":"
        + quote(complaints)
        + ",\"mentalStatus\":"
        + quote(mentalStatus)
        + ",\"diagnosis\":"
        + quote(diagnosis)
        + ",\"recommendations\":"
        + quote(recommendations)
        + "}";
  }

  static String quote(String value) {
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
}
