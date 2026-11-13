package ru.quietclinic.drive;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface DriveVersionRepository extends JpaRepository<DriveVersion, UUID> {
  List<DriveVersion> findByNodeIdOrderByNumberDesc(UUID nodeId);

  Optional<DriveVersion> findByNodeIdAndNumber(UUID nodeId, int number);
}
