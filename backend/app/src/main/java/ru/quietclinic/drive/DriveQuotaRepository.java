package ru.quietclinic.drive;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface DriveQuotaRepository extends JpaRepository<DriveQuota, UUID> {}
