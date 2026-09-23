package com.codgo.ulock.auth;

import com.codgo.ulock.audit.AuditAction;
import com.codgo.ulock.audit.AuditService;
import com.codgo.ulock.audit.AuditTarget;
import com.codgo.ulock.auth.AuthDtos.ClientTokenResponse;
import com.codgo.ulock.auth.MachineClientDtos.CreatedMachineClientResponse;
import com.codgo.ulock.auth.MachineClientDtos.UpdateMachineClientRequest;
import com.codgo.ulock.common.PlatformTenant;
import com.codgo.ulock.common.error.AuthenticationFailedException;
import com.codgo.ulock.common.error.NotFoundException;
import com.codgo.ulock.common.security.TokenClaims;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Machine clients are platform-level: they are managed, and audited, in the platform tenant. */
@Service
class MachineClientService {

    static final String INVALID_CLIENT = "Invalid client credentials";
    private static final int CLIENT_SECRET_BYTES = 32;
    private static final int CLIENT_ID_BYTES = 12;

    private final MachineClientRepository clients;
    private final PasswordEncoder passwordEncoder;
    private final AccessTokenIssuer accessTokens;
    private final AuditService audit;
    private final String dummySecretHash;

    MachineClientService(MachineClientRepository clients, PasswordEncoder passwordEncoder,
                         AccessTokenIssuer accessTokens, AuditService audit) {
        this.clients = clients;
        this.passwordEncoder = passwordEncoder;
        this.accessTokens = accessTokens;
        this.audit = audit;
        this.dummySecretHash = passwordEncoder.encode(SecureTokens.random(16));
    }

    @Transactional
    CreatedMachineClientResponse create(String name) {
        String clientId = "ulk_" + SecureTokens.random(CLIENT_ID_BYTES);
        String secret = SecureTokens.random(CLIENT_SECRET_BYTES);
        MachineClient client = clients.save(new MachineClient(clientId, name.trim(), passwordEncoder.encode(secret)));
        audit.record(PlatformTenant.ID, AuditAction.MACHINE_CLIENT_CREATED,
                AuditTarget.of(AuditTarget.MACHINE_CLIENT, client.getId()),
                Map.of("clientId", clientId, "name", client.getName()));
        return new CreatedMachineClientResponse(client.getId(), clientId, secret, client.getName(), client.getStatus());
    }

    @Transactional(readOnly = true)
    Page<MachineClient> list(Pageable pageable) {
        return clients.findAll(pageable);
    }

    @Transactional
    MachineClient update(UUID id, UpdateMachineClientRequest request) {
        MachineClient client = clients.findById(id).orElseThrow(() -> new NotFoundException("Machine client", id));
        Map<String, Object> changes = new HashMap<>();
        if (request.name() != null && !request.name().trim().equals(client.getName())) {
            client.rename(request.name().trim());
            changes.put("name", client.getName());
        }
        if (request.status() != null && request.status() != client.getStatus()) {
            client.changeStatus(request.status());
            changes.put("status", client.getStatus());
        }
        if (!changes.isEmpty()) {
            audit.record(PlatformTenant.ID, AuditAction.MACHINE_CLIENT_UPDATED,
                    AuditTarget.of(AuditTarget.MACHINE_CLIENT, id), changes);
        }
        return client;
    }

    /** OAuth 2.0 client-credentials grant (RFC 6749 section 4.4). */
    @Transactional(readOnly = true)
    ClientTokenResponse issueToken(String clientId, String clientSecret) {
        MachineClient client = clients.findByClientId(clientId).orElse(null);
        String hash = client == null ? dummySecretHash : client.getClientSecretHash();
        boolean secretMatches = passwordEncoder.matches(clientSecret, hash);
        if (client == null || !secretMatches || !client.isActive()) {
            throw new AuthenticationFailedException(INVALID_CLIENT);
        }
        return new ClientTokenResponse(accessTokens.forClient(clientId), "Bearer", accessTokens.ttlSeconds(),
                TokenClaims.AUTHZ_CHECK_SCOPE);
    }

    boolean isActive(String clientId) {
        return clients.existsByClientIdAndStatus(clientId, MachineClientStatus.ACTIVE);
    }
}
