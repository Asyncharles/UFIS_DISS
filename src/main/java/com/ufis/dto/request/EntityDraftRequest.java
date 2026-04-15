package com.ufis.dto.request;

import com.ufis.domain.enums.LegalEntityType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record EntityDraftRequest(
        @NotBlank String name,
        @NotNull LegalEntityType type
) {
}
