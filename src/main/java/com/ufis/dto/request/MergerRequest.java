package com.ufis.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MergerRequest(
        @NotNull Instant validDate,
        @NotBlank String description,
        @NotEmpty
        @Size(min = 2)
        List<@NotNull UUID> sourceEntityIds,
        @NotNull @Valid EntityDraftRequest resultEntity,
        List<@Valid MergerSecurityMappingRequest> securityMappings
) {
}
