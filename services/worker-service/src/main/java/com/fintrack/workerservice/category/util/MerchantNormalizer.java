package com.fintrack.workerservice.category.util;

import java.util.Locale;

public final class MerchantNormalizer {

    private MerchantNormalizer() {
    }

    public static String normalize(String value) {
        if (value == null) {
            return "";
        }

        return value
                .strip()
                .replaceAll("\\s+", " ")
                .toUpperCase(Locale.ROOT);
    }
}