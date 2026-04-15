package com.ufis.dto.response;

import java.util.List;

public record NameHistoryResponse(
        List<SecurityNameHistoryEntryResponse> security,
        List<IssuerNameHistoryEntryResponse> issuer
) {
}
