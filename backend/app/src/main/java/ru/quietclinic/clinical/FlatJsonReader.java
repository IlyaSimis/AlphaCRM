package ru.quietclinic.clinical;

/** Читатель плоского JSON-объекта со строковыми значениями: снимок подписанного документа. */
final class FlatJsonReader {
  private final String text;
  private int pos;

  FlatJsonReader(String text) {
    this.text = text;
  }

  boolean atEnd() {
    skipSpaces();
    return pos == text.length();
  }

  boolean consume(char expected) {
    skipSpaces();
    if (pos < text.length() && text.charAt(pos) == expected) {
      pos++;
      return true;
    }
    return false;
  }

  String string() {
    if (!consume('"')) return null;
    var out = new StringBuilder();
    while (pos < text.length()) {
      var ch = text.charAt(pos++);
      if (ch == '"') return out.toString();
      if (ch != '\\') {
        out.append(ch);
        continue;
      }
      if (pos >= text.length()) return null;
      var escaped = text.charAt(pos++);
      switch (escaped) {
        case '"', '\\', '/' -> out.append(escaped);
        case 'n' -> out.append('\n');
        case 'r' -> out.append('\r');
        case 't' -> out.append('\t');
        case 'b' -> out.append('\b');
        case 'f' -> out.append('\f');
        case 'u' -> {
          if (pos + 4 > text.length()) return null;
          try {
            out.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16));
          } catch (NumberFormatException ex) {
            return null;
          }
          pos += 4;
        }
        default -> {
          return null;
        }
      }
    }
    return null;
  }

  private void skipSpaces() {
    while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) pos++;
  }
}
