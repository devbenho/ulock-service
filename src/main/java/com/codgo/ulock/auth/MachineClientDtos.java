package com.codgo.ulock.auth;

import static com.codgo.ulock.common.web.ValidationPatterns.NOT_BLANK;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

final class MachineClientDtos {

    private MachineClientDtos() {}

    record CreateMachineClientRequest(@NotBlank @Size(max = 200) String name) {}

    /** Partial update: null fields are left unchanged. */
    record UpdateMachineClientRequest(
            @Size(min = 1, max = 200) @Pattern(regexp = NOT_BLANK, message = "must not be blank") String name,
            MachineClientStatus status) {}

    record MachineClientResponse(UUID id, String clientId, String name, MachineClientStatus status,
                                 Instant createdAt, Instant updatedAt) {

        static MachineClientResponse from(MachineClient client) {
            return new MachineClientResponse(client.getId(), client.getClientId(), client.getName(),
                    client.getStatus(), client.getCreatedAt(), client.getUpdatedAt());
        }
    }

    /** Returned once, at creation: the secret is stored only as a hash. */
    record CreatedMachineClientResponse(UUID id, String clientId, String clientSecret, String name,
                                        MachineClientStatus status) {}
}
