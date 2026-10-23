package ru.quietclinic.registry;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface WaitlistRepository extends JpaRepository<WaitlistEntry, UUID> {
  List<WaitlistEntry> findByStatusOrderByWishFrom(String status);
}
