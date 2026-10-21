package ru.quietclinic.patients;

import static ru.quietclinic.platform.PlatformConfig.actorId;
import static ru.quietclinic.platform.PlatformConfig.notFound;
import static ru.quietclinic.platform.PlatformConfig.requireRole;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.quietclinic.audit.AuditService;

@Entity
@Table(name = "patient")
class Patient {
  @Id UUID id;

  @Column(nullable = false)
  String fullName;

  @Column(nullable = false)
  LocalDate birthDate;

  @Column(nullable = false)
  Instant createdAt;

  @OneToMany(mappedBy = "patient", cascade = CascadeType.ALL, orphanRemoval = true)
  List<Contact> contacts = new ArrayList<>();

  @OneToMany(mappedBy = "patient", cascade = CascadeType.ALL, orphanRemoval = true)
  List<Consent> consents = new ArrayList<>();

  protected Patient() {}

  Patient(String fullName, LocalDate birthDate) {
    id = UUID.randomUUID();
    this.fullName = fullName;
    this.birthDate = birthDate;
    createdAt = Instant.now();
  }
}

@Entity
@Table(name = "patient_contact")
class Contact {
  @Id UUID id;

  @jakarta.persistence.ManyToOne(optional = false)
  Patient patient;

  @Column(nullable = false)
  String type;

  @Column(name = "contact_value", nullable = false)
  String value;

  protected Contact() {}

  Contact(Patient patient, String type, String value) {
    id = UUID.randomUUID();
    this.patient = patient;
    this.type = type;
    this.value = value;
  }
}

@Entity
@Table(name = "patient_consent")
class Consent {
  @Id UUID id;

  @jakarta.persistence.ManyToOne(optional = false)
  Patient patient;

  @Column(nullable = false)
  String version;

  @Column(nullable = false)
  String basis;

  @Column(nullable = false)
  Instant grantedAt;

  Instant revokedAt;

  protected Consent() {}

  Consent(Patient patient, String version, String basis) {
    id = UUID.randomUUID();
    this.patient = patient;
    this.version = version;
    this.basis = basis;
    grantedAt = Instant.now();
  }
}

interface PatientRepository extends JpaRepository<Patient, UUID> {
  List<Patient> findTop20ByFullNameContainingIgnoreCaseOrderByFullName(String query);
}

record CreatePatientRequest(
    @NotBlank String fullName,
    @NotNull LocalDate birthDate,
    @NotBlank String phone,
    @NotBlank String consentVersion,
    @NotBlank String consentBasis) {}

record UpdatePatientRequest(
    @NotBlank @Size(max = 255) String fullName,
    @NotNull @Past LocalDate birthDate,
    @NotBlank @Size(max = 255) String phone) {}

record PatientView(
    UUID id,
    String fullName,
    LocalDate birthDate,
    List<ContactView> contacts,
    List<ConsentView> consents) {}

record ContactView(String type, String value) {}

record ConsentView(String version, String basis, Instant grantedAt, Instant revokedAt) {}

@RestController
@RequestMapping("/api/v1/patients")
public class PatientsController {
  private final PatientRepository patients;
  private final AuditService audit;

  PatientsController(PatientRepository patients, AuditService audit) {
    this.patients = patients;
    this.audit = audit;
  }

  @GetMapping
  @Transactional(readOnly = true)
  List<PatientView> search(@RequestParam(defaultValue = "") String query) {
    requireRole("ADMINISTRATOR", "DOCTOR", "NURSE", "CASHIER", "MANAGER");
    var result =
        (query.isBlank()
                ? patients.findAll()
                : patients.findTop20ByFullNameContainingIgnoreCaseOrderByFullName(query))
            .stream().map(this::view).toList();
    result.forEach(
        p -> audit.record(actorId(), "PATIENT_ADMIN_READ", "PATIENT", p.id(), "ALLOWED", "search"));
    return result;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Transactional
  PatientView create(@Valid @RequestBody CreatePatientRequest request) {
    requireRole("ADMINISTRATOR");
    var patient = new Patient(request.fullName(), request.birthDate());
    patient.contacts.add(new Contact(patient, "PHONE", request.phone()));
    patient.consents.add(new Consent(patient, request.consentVersion(), request.consentBasis()));
    patients.save(patient);
    audit.record(actorId(), "PATIENT_CREATED", "PATIENT", patient.id, "ALLOWED", "registration");
    return view(patient);
  }

  @GetMapping("/{id}")
  @Transactional(readOnly = true)
  PatientView get(@PathVariable UUID id) {
    requireRole("ADMINISTRATOR", "DOCTOR", "NURSE", "CASHIER", "MANAGER");
    var patient = patients.findById(id).orElseThrow(() -> notFound("Пациент не найден"));
    audit.record(actorId(), "PATIENT_ADMIN_READ", "PATIENT", patient.id, "ALLOWED", "card");
    return view(patient);
  }

  @PutMapping("/{id}")
  @Transactional
  PatientView update(@PathVariable UUID id, @Valid @RequestBody UpdatePatientRequest request) {
    requireRole("ADMINISTRATOR");
    var patient = patients.findById(id).orElseThrow(() -> notFound("Пациент не найден"));
    var changed = new ArrayList<String>();
    var fullName = request.fullName().trim();
    if (!fullName.equals(patient.fullName)) {
      patient.fullName = fullName;
      changed.add("fullName");
    }
    if (!request.birthDate().equals(patient.birthDate)) {
      patient.birthDate = request.birthDate();
      changed.add("birthDate");
    }
    var phone = request.phone().trim();
    var current = patient.contacts.stream().filter(c -> "PHONE".equals(c.type)).findFirst();
    if (current.isEmpty()) {
      patient.contacts.add(new Contact(patient, "PHONE", phone));
      changed.add("phone");
    } else if (!phone.equals(current.get().value)) {
      current.get().value = phone;
      changed.add("phone");
    }
    patients.save(patient);
    // В аудит пишутся только имена изменённых полей, без значений.
    audit.record(
        actorId(),
        "PATIENT_UPDATED",
        "PATIENT",
        patient.id,
        "ALLOWED",
        changed.isEmpty() ? "no-changes" : String.join(",", changed));
    return view(patient);
  }

  private PatientView view(Patient patient) {
    return new PatientView(
        patient.id,
        patient.fullName,
        patient.birthDate,
        patient.contacts.stream().map(c -> new ContactView(c.type, c.value)).toList(),
        patient.consents.stream()
            .map(c -> new ConsentView(c.version, c.basis, c.grantedAt, c.revokedAt))
            .toList());
  }
}
