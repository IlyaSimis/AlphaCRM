package ru.quietclinic.staff;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Условия работы и ставки сотрудников (раздел F, identity/hr). */
@RestController
@RequestMapping("/api/v1/hr/contracts")
public class HrController {
  private final HrService hr;

  HrController(HrService hr) {
    this.hr = hr;
  }

  @GetMapping("/me")
  List<HrContractView> mine() {
    return hr.mine();
  }

  @GetMapping
  List<HrContractView> current() {
    return hr.current();
  }

  @GetMapping("/{staffId}")
  List<HrContractView> history(@PathVariable UUID staffId) {
    return hr.of(staffId);
  }

  @PutMapping("/{staffId}")
  HrContractView change(@PathVariable UUID staffId, @Valid @RequestBody HrContractRequest request) {
    return hr.change(staffId, request);
  }
}
