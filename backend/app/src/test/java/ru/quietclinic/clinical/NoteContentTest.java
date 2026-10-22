package ru.quietclinic.clinical;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NoteContentTest {
  @Test
  void buildsStableJsonFromFourNoteFields() {
    assertThat(NoteContent.canonical("Жалобы", "Статус", "Диагноз", "Рекомендации"))
        .isEqualTo(
            "{\"complaints\":\"Жалобы\",\"mentalStatus\":\"Статус\","
                + "\"diagnosis\":\"Диагноз\",\"recommendations\":\"Рекомендации\"}");
  }

  @Test
  void escapesQuotesBackslashesAndLineBreaks() {
    assertThat(NoteContent.quote("a\"b\\c\nd\te\u0001"))
        .isEqualTo("\"a\\\"b\\\\c\\nd\\te\\u0001\"");
  }
}
