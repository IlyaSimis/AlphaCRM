package ru.quietclinic.clinical;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NoteContentTest {
  @Test
  void buildsStableJsonFromFourNoteFields() {
    assertThat(NoteContent.canonical("Complaints", "Status", "Diagnosis", "Advice"))
        .isEqualTo(
            "{\"complaints\":\"Complaints\",\"mentalStatus\":\"Status\","
                + "\"diagnosis\":\"Diagnosis\",\"recommendations\":\"Advice\"}");
  }

  @Test
  void escapesQuotesBackslashesAndLineBreaks() {
    assertThat(NoteContent.quote("a\"b\\c\nd\te\u0001"))
        .isEqualTo("\"a\\\"b\\\\c\\nd\\te\\u0001\"");
  }

  @Test
  void readsFieldsBackFromSignedSnapshot() {
    var snapshot =
        NoteContent.canonical("Complaints \"in quotes\"", "Status\nsecond line", "F41", "");
    var fields = NoteContent.parse(snapshot);
    assertThat(fields.complaints()).isEqualTo("Complaints \"in quotes\"");
    assertThat(fields.mentalStatus()).isEqualTo("Status\nsecond line");
    assertThat(fields.diagnosis()).isEqualTo("F41");
    assertThat(fields.recommendations()).isEmpty();
  }

  @Test
  void unknownSnapshotFormatGivesEmptyFieldsInsteadOfError() {
    assertThat(NoteContent.parse("plain text")).isEqualTo(SnapshotFields.EMPTY);
    assertThat(NoteContent.parse("{\"diagnosis\":")).isEqualTo(SnapshotFields.EMPTY);
    assertThat(NoteContent.parse(null)).isEqualTo(SnapshotFields.EMPTY);
    // Кириллица здесь — проверка разбора юникод-экранирования, а не надпись.
    assertThat(NoteContent.parse("{\"diagnosis\":\"\\u0414\"}").diagnosis()).isEqualTo("Д");
  }
}
