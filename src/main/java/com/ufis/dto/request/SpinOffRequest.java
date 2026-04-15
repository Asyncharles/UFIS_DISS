package com.ufis.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SpinOffRequest(
        @NotNull Instant validDate,
        @NotBlank String description,
        @NotNull UUID parentEntityId,
        @NotNull @Valid EntityDraftRequest newEntity,
        List<@Valid SpinOffSecurityMappingRequest> securityMappings
) {
}
