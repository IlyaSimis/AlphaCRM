package ru.quietclinic.staff;

import static ru.quietclinic.platform.PlatformConfig.actorId;
import static ru.quietclinic.platform.PlatformConfig.clinicId;
import static ru.quietclinic.platform.PlatformConfig.conflict;
import static ru.quietclinic.platform.PlatformConfig.notFound;
import static ru.quietclinic.platform.PlatformConfig.requirePermission;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.quietclinic.audit.AuditService;
import ru.quietclinic.platform.Permission;

/**
 * Условия работы и ставки (раздел C, hr): чужие видит и меняет только владелец, свои — каждый
 * сотрудник. В журнал — факт чтения и изменения, без сумм и должностей.
 */
@Service
class HrService {
  private final HrContractRepository contracts;
  private final StaffRepository staff;
  private final AuthService auth;
  private final AuditService audit;

  HrService(
      HrContractRepository contracts, StaffRepository staff, AuthService auth, AuditService audit) {
    this.contracts = contracts;
    this.staff = staff;
    this.auth = auth;
    this.audit = audit;
  }

  /** Свои условия работы: история, новые сверху. */
  @Transactional(readOnly = true)
  List<HrContractView> mine() {
    requirePermission(Permission.HR_READ_OWN, Permission.HR_MANAGE);
    StaffUser me = auth.current();
    return history(me);
  }

  /** Действующие условия всех сотрудников клиники — владелец. */
  @Transactional(readOnly = true)
  List<HrContractView> current() {
    requirePermission(Permission.HR_MANAGE);
    Map<UUID, String> names = new HashMap<>();
    staff
        .findByClinicIdOrderByFullName(clinicId())
        .forEach(user -> names.put(user.id, user.fullName));
    audit.record(actorId(), "HR_CONTRACT_LIST_READ", "CLINIC", clinicId(), "ALLOWED", null);
    return contracts.findByClinicIdAndValidToIsNull(clinicId()).stream()
        .filter(contract -> names.containsKey(contract.staffId))
        .map(contract -> HrContractView.of(contract, names.get(contract.staffId)))
        .sorted(Comparator.comparing(HrContractView::fullName))
        .toList();
  }

  /** История условий сотрудника — владелец. */
  @Transactional(readOnly = true)
  List<HrContractView> of(UUID staffId) {
    requirePermission(Permission.HR_MANAGE);
    StaffUser user = find(staffId);
    audit.record(actorId(), "HR_CONTRACT_READ", "STAFF", user.id, "ALLOWED", null);
    return history(user);
  }

  /** Новые условия с даты: прежние действующие закрываются днём раньше. */
  @Transactional
  HrContractView change(UUID staffId, HrContractRequest request) {
    requirePermission(Permission.HR_MANAGE);
    StaffUser user = find(staffId);
    Optional<HrContract> open =
        contracts.findByStaffIdOrderByValidFromDesc(user.id).stream()
            .filter(contract -> contract.validTo == null)
            .findFirst();
    if (open.isPresent()) {
      if (!request.validFrom().isAfter(open.get().validFrom)) {
        throw conflict(
            "CONTRACT_DATE_TOO_EARLY", "New terms must start after the current ones started");
      }
      open.get().validTo = request.validFrom().minusDays(1);
    }
    HrContract contract =
        contracts.saveAndFlush(
            new HrContract(
                clinicId(),
                user.id,
                request.position().trim(),
                request.rateMinor(),
                request.rateUnit(),
                request.currency() == null ? "RUB" : request.currency(),
                request.validFrom(),
                actorId()));
    audit.record(actorId(), "HR_CONTRACT_CHANGED", "STAFF", user.id, "ALLOWED", null);
    return HrContractView.of(contract, user.fullName);
  }

  private List<HrContractView> history(StaffUser user) {
    return contracts.findByStaffIdOrderByValidFromDesc(user.id).stream()
        .map(contract -> HrContractView.of(contract, user.fullName))
        .toList();
  }

  private StaffUser find(UUID id) {
    return staff
        .findById(id)
        .filter(user -> user.clinicId.equals(clinicId()))
        .orElseThrow(() -> notFound("STAFF_NOT_FOUND", "Staff member not found"));
  }
}
