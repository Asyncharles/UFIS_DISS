package com.ufis.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record MergerSecurityMappingRequest(
        @NotEmpty
        @Size(min = 2)
        List<@NotNull UUID> sourceSecurityIds,
        @NotNull @Valid SecurityDraftRequest resultSecurity
) {
}
