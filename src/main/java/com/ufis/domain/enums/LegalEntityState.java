package com.ufis.domain.enums;

public enum LegalEntityState {
    ACTIVE,
    MERGED,
    ACQUIRED;

    public boolean isTerminal() {
        return this != ACTIVE;
    }
}
