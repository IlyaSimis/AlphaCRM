package ru.quietclinic.staff;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ClinicRepository extends JpaRepository<Clinic, UUID> {
  List<Clinic> findAllByOrderByCreatedAt();
}
