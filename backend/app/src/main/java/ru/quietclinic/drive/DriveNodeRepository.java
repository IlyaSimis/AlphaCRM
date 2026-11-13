package ru.quietclinic.drive;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface DriveNodeRepository extends JpaRepository<DriveNode, UUID> {
  Optional<DriveNode> findByIdAndClinicId(UUID id, UUID clinicId);

  List<DriveNode> findByClinicIdAndParentIdAndDeletedAtIsNull(UUID clinicId, UUID parentId);

  List<DriveNode> findByClinicIdAndParentIdIsNullAndOwnerIdAndDeletedAtIsNull(
      UUID clinicId, UUID ownerId);

  List<DriveNode> findByClinicIdAndIdInAndDeletedAtIsNull(UUID clinicId, Collection<UUID> ids);

  List<DriveNode> findByClinicIdAndDeletedAtIsNotNull(UUID clinicId);

  List<DriveNode> findByDeletedAtBefore(Instant before);

  List<DriveNode> findByParentId(UUID parentId);
}
