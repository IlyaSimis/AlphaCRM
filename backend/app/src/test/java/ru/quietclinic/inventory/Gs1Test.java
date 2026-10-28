package ru.quietclinic.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** Разбор DataMatrix GS1 со сканера (раздел G): AI 01, 21, 17, 10; криптохвост отбрасывается. */
class Gs1Test {

  @Test
  void readsDrugCodeWithGroupSeparatorsAndDropsCryptoTail() {
    var code = Gs1.parse("0104601234567893215TestSerial1\u001d91EE07\u001d92dGVzdA==");
    assertThat(code.gtin()).isEqualTo("04601234567893");
    assertThat(code.serial()).isEqualTo("5TestSerial1");
    assertThat(code.sgtin()).isEqualTo("046012345678935TestSerial1");
  }

  @Test
  void readsBracketedFormWithExpiryAndBatch() {
    var code = Gs1.parse("(01)04601234567893(21)ABC123(17)271231(10)LOT7");
    assertThat(code.serial()).isEqualTo("ABC123");
    assertThat(code.expiresOn()).isEqualTo(LocalDate.of(2027, 12, 31));
    assertThat(code.batch()).isEqualTo("LOT7");
  }

  @Test
  void dayZeroMeansEndOfMonthAndSymbologyPrefixIsIgnored() {
    var code = Gs1.parse("]d20104601234567893172802001010A\u001d21XYZ");
    assertThat(code.expiresOn()).isEqualTo(LocalDate.of(2028, 2, 29));
    assertThat(code.batch()).isEqualTo("10A");
    assertThat(code.serial()).isEqualTo("XYZ");
  }

  @Test
  void sgtinWithoutAisIsAcceptedAsIs() {
    var code = MarkingService.read("04601234567893SERIAL42");
    assertThat(code.gtin()).isEqualTo("04601234567893");
    assertThat(code.serial()).isEqualTo("SERIAL42");
  }

  @Test
  void rejectsGarbage() {
    assertThatThrownBy(() -> Gs1.parse("hello"))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("could not be read");
    assertThatThrownBy(() -> Gs1.parse("(01)123(21)X")).isInstanceOf(RuntimeException.class);
  }
}
