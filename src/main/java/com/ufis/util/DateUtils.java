package com.ufis.util;

import java.time.Instant;
import java.util.Date;

public final class DateUtils {
    public static Instant toInstant(Date date) {
        return date == null ? null : date.toInstant();
    }
}