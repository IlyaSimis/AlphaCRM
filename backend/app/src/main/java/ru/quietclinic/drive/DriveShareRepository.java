package ru.quietclinic.drive;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface DriveShareRepository extends JpaRepository<DriveShare, UUID> {
  List<DriveShare> findByNodeIdIn(Collection<UUID> nodeIds);

  List<DriveShare> findByNodeIdOrderByCreatedAt(UUID nodeId);

  Optional<DriveShare> findByLinkTokenHash(String linkTokenHash);

  List<DriveShare> findByPrincipalTypeInAndPrincipalIdIn(
      Collection<String> types, Collection<String> principalIds);

  List<DriveShare> findByPrincipalType(String principalType);

  void deleteByNodeId(UUID nodeId);
}
