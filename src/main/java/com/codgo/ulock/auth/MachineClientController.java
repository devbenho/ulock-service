package com.codgo.ulock.auth;

import com.codgo.ulock.auth.MachineClientDtos.CreateMachineClientRequest;
import com.codgo.ulock.auth.MachineClientDtos.CreatedMachineClientResponse;
import com.codgo.ulock.auth.MachineClientDtos.MachineClientResponse;
import com.codgo.ulock.auth.MachineClientDtos.UpdateMachineClientRequest;
import com.codgo.ulock.common.web.PageResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.SortDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/machine-clients")
@PreAuthorize("hasAuthority('client:manage')")
class MachineClientController {

    private final MachineClientService service;

    MachineClientController(MachineClientService service) {
        this.service = service;
    }

    @PostMapping
    ResponseEntity<CreatedMachineClientResponse> create(@Valid @RequestBody CreateMachineClientRequest request) {
        CreatedMachineClientResponse created = service.create(request.name());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    PageResponse<MachineClientResponse> list(@SortDefault(sort = {"name", "id"}) Pageable pageable) {
        return PageResponse.of(service.list(pageable), MachineClientResponse::from);
    }

    @PatchMapping("/{id}")
    MachineClientResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateMachineClientRequest request) {
        return MachineClientResponse.from(service.update(id, request));
    }
}
