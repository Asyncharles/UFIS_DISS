package com.ufis.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record SecurityRenameRequest(
        @NotNull UUID securityId,
        @NotBlank String newName,
        String newIsin
) {
}
