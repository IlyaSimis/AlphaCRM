package ru.quietclinic.clinical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class MedicalDocumentTest {
  @Test
  void signingLocksExactSnapshotAndProducesHash() {
    var document = new MedicalDocument(UUID.randomUUID(), null, "v2.4", "encrypted");

    document.sign(UUID.randomUUID(), "{\"diagnosis\":\"synthetic\"}");

    assertThat(document.state).isEqualTo("SIGNED_LOCKED");
    assertThat(document.contentHash).hasSize(64);
    assertThatThrownBy(() -> document.updateSnapshot("changed")).hasMessageContaining("неизменяем");
  }

  @Test
  void amendmentKeepsReferenceToOriginal() {
    var original = new MedicalDocument(UUID.randomUUID(), null, "v2.4", "encrypted");
    original.sign(UUID.randomUUID(), "content");

    var amendment =
        new MedicalDocument(original.encounterId, original.id, "v2.4", "encrypted-amendment");

    assertThat(amendment.amendsDocumentId).isEqualTo(original.id);
    assertThat(original.state).isEqualTo("SIGNED_LOCKED");
  }
}
