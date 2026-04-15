package com.ufis.dto.response;

import java.util.UUID;

public record IssuerRefResponse(
        UUID id,
        String name
) {
}
