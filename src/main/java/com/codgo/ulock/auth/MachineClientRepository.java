package com.codgo.ulock.auth;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface MachineClientRepository extends JpaRepository<MachineClient, UUID> {

    Optional<MachineClient> findByClientId(String clientId);

    boolean existsByClientIdAndStatus(String clientId, MachineClientStatus status);
}
