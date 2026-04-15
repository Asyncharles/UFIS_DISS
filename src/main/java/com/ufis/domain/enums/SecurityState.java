package com.ufis.domain.enums;

public enum SecurityState {
    ACTIVE,
    MERGED,
    SPLIT,
    REDEEMED;

    public boolean isTerminal() {
        return this != ACTIVE;
    }
}
