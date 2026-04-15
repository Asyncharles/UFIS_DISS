package com.ufis.dto.request;

import com.ufis.domain.enums.SecurityType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record SecurityDraftRequest(
        @NotBlank String name,
        @NotNull SecurityType type,
        String isin,
        Instant maturityDate
) {
}
