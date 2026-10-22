package ru.quietclinic.clinical;

/** Каноническое содержимое документа из четырёх полей заметки: от него считается хеш подписи. */
final class NoteContent {
  private NoteContent() {}

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
