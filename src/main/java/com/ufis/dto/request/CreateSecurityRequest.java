package com.ufis.dto.request;

import com.ufis.domain.enums.SecurityType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public record CreateSecurityRequest(
        @NotBlank String name,
        @NotNull SecurityType type,
        @NotNull UUID issuerId,
        @NotNull Instant issueDate,
        String isin,
        Instant maturityDate
) {
}
