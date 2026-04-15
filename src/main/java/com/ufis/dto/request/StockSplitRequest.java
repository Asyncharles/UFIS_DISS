package com.ufis.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;
import java.util.UUID;

public record StockSplitRequest(
        @NotNull Instant validDate,
        @NotBlank String description,
        @NotNull UUID sourceSecurityId,
        @NotBlank
        @Pattern(regexp = "^\\d+:\\d+$")
        String splitRatio,
        @NotNull @Valid SecurityDraftRequest resultSecurity
) {
}
