package com.ufis.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public record AcquisitionRequest(
        @NotNull Instant validDate,
        @NotBlank String description,
        @NotNull UUID acquirerEntityId,
        @NotNull UUID targetEntityId
) {
}
