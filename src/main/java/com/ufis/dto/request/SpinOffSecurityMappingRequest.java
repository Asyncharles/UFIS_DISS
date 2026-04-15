package com.ufis.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record SpinOffSecurityMappingRequest(
        @NotNull UUID parentSecurityId,
        @NotNull @Valid SecurityDraftRequest newSecurity
) {
}
