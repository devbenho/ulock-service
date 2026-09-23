package com.codgo.ulock.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** An external system allowed to call the authorization check with a client-credentials token. */
@Entity
@Table(name = "machine_clients")
class MachineClient {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private String clientId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String clientSecretHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MachineClientStatus status;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    protected MachineClient() {}

    MachineClient(String clientId, String name, String clientSecretHash) {
        this.clientId = clientId;
        this.name = name;
        this.clientSecretHash = clientSecretHash;
        this.status = MachineClientStatus.ACTIVE;
    }

    boolean isActive() {
        return status == MachineClientStatus.ACTIVE;
    }

    void rename(String name) {
        this.name = name;
    }

    void changeStatus(MachineClientStatus status) {
        this.status = status;
    }

    UUID getId() { return id; }
    String getClientId() { return clientId; }
    String getName() { return name; }
    String getClientSecretHash() { return clientSecretHash; }
    MachineClientStatus getStatus() { return status; }
    Instant getCreatedAt() { return createdAt; }
    Instant getUpdatedAt() { return updatedAt; }
}
