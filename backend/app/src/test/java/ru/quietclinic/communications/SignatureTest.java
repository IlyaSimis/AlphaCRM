package ru.quietclinic.communications;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SignatureTest {
  @Test
  void signsTimestampAndBodyWithHmacSha256() {
    assertThat(Signature.sign("secret-of-at-least-32-characters!!", 1_700_000_000L, "{\"a\":1}"))
        .isEqualTo("sha256=e3ddc1ca62bb09a4e590fdedc53fb0de19ca2d8af4d1b103e817984612cf05ff");
  }

  @Test
  void anotherTimestampGivesAnotherSignature() {
    var secret = "secret-of-at-least-32-characters!!";
    assertThat(Signature.sign(secret, 1L, "{}")).isNotEqualTo(Signature.sign(secret, 2L, "{}"));
  }
}
