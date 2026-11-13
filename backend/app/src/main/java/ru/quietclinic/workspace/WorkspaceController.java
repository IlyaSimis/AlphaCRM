package ru.quietclinic.workspace;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/workspace")
public class WorkspaceController {
  private final WorkspaceService workspace;

  WorkspaceController(WorkspaceService workspace) {
    this.workspace = workspace;
  }

  @GetMapping("/items")
  List<WorkspaceItemView> items(@RequestParam(defaultValue = "false") boolean archived) {
    return workspace.items(archived);
  }

  @GetMapping("/assignees")
  List<WorkspaceAssigneeView> assignees() {
    return workspace.assignees();
  }

  @PostMapping("/items")
  @ResponseStatus(HttpStatus.CREATED)
  WorkspaceItemView create(@Valid @RequestBody WorkspaceItemRequest request) {
    return workspace.create(request);
  }

  @PutMapping("/items/{id}")
  WorkspaceItemView update(
      @PathVariable UUID id, @Valid @RequestBody WorkspaceItemRequest request) {
    return workspace.update(id, request);
  }

  @PostMapping("/items/{id}/archive")
  WorkspaceItemView archive(@PathVariable UUID id) {
    return workspace.archive(id, true);
  }

  @PostMapping("/items/{id}/restore")
  WorkspaceItemView restore(@PathVariable UUID id) {
    return workspace.archive(id, false);
  }

  @GetMapping("/views")
  List<SavedView> views() {
    return workspace.views();
  }

  @PostMapping("/views")
  @ResponseStatus(HttpStatus.CREATED)
  SavedView createView(@Valid @RequestBody SavedViewRequest request) {
    return workspace.createView(request);
  }

  @DeleteMapping("/views/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void deleteView(@PathVariable UUID id) {
    workspace.deleteView(id);
  }
}
