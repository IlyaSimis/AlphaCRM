package ru.quietclinic.communications;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface OutboxRepository extends JpaRepository<OutboxMessage, UUID> {}
