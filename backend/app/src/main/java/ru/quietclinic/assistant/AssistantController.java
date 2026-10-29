package ru.quietclinic.assistant;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/assistant")
public class AssistantController {
  private final AssistantService assistant;

  AssistantController(AssistantService assistant) {
    this.assistant = assistant;
  }

  @GetMapping("/status")
  StatusView status() {
    return assistant.status();
  }

  @PostMapping("/navigate")
  NavigationView navigate(@Valid @RequestBody NavigateRequest request) {
    return assistant.navigate(request);
  }

  public record NavigateRequest(
      @NotBlank @Size(max = 500) String query,
      @Size(max = 60) String currentPage,
      @NotEmpty @Size(max = 60) List<@Valid Destination> destinations) {}

  /** Только безопасные метаданные страницы: ни записей пациентов, ни содержимого пространства. */
  public record Destination(
      @NotBlank @Pattern(regexp = "[a-z0-9-]{1,60}") String id,
      @NotBlank @Size(max = 120) String title,
      @Size(max = 500) String description,
      @Size(max = 16) List<@Size(max = 80) String> keywords) {}

  public record StatusView(boolean enabled, String model) {}

  public record NavigationView(String destinationId, String source) {}
}
