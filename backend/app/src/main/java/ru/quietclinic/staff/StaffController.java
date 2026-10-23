package ru.quietclinic.staff;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/staff")
public class StaffController {
  private final StaffService staff;

  StaffController(StaffService staff) {
    this.staff = staff;
  }

  @GetMapping
  List<StaffView> list() {
    return staff.list();
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  StaffView create(@Valid @RequestBody CreateStaffRequest request) {
    return staff.create(request);
  }

  @PostMapping("/{id}/deactivate")
  StaffView deactivate(@PathVariable UUID id) {
    return staff.setActive(id, false);
  }

  @PostMapping("/{id}/activate")
  StaffView activate(@PathVariable UUID id) {
    return staff.setActive(id, true);
  }

  @PostMapping("/{id}/password")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void resetPassword(@PathVariable UUID id, @Valid @RequestBody PasswordResetRequest request) {
    staff.resetPassword(id, request);
  }
}
