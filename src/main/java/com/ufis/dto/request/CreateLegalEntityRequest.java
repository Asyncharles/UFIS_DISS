package com.ufis.dto.request;

import com.ufis.domain.enums.LegalEntityType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record CreateLegalEntityRequest(
        @NotBlank String name,
        @NotNull LegalEntityType type,
        @NotNull Instant foundedDate
) {
}
