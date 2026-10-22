package ru.quietclinic.scheduling;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface PractitionerRepository extends JpaRepository<Practitioner, UUID> {}
