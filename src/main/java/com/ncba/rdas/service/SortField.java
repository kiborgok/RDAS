package com.ncba.rdas.service;

import com.ncba.rdas.domain.Country;
import java.util.Comparator;
import java.util.Locale;
import java.util.function.Function;

/**
 * Whitelist of fields a client may sort by. Restricting to an enum prevents
 * arbitrary/injection-style sort expressions and gives a clean validation error
 * for unknown values.
 */
public enum SortField {
    NAME("name", Country::name),
    CODE("code", Country::isoCode),
    CAPITAL("capital", Country::capitalCity),
    CONTINENT("continent", Country::continentName),
    CURRENCY("currency", Country::currencyName),
    PHONE_CODE("phoneCode", Country::phoneCode);

    private final String apiName;
    private final Function<Country, String> accessor;

    SortField(String apiName, Function<Country, String> accessor) {
        this.apiName = apiName;
        this.accessor = accessor;
    }

    public String apiName() {
        return apiName;
    }

    /** Null-safe, case-insensitive comparator for this field. */
    public Comparator<Country> comparator() {
        return Comparator.comparing(
                c -> {
                    String v = accessor.apply(c);
                    return v == null ? "" : v.toLowerCase(Locale.ROOT);
                });
    }

    public static SortField fromApiName(String value) {
        for (SortField f : values()) {
            if (f.apiName.equalsIgnoreCase(value)) {
                return f;
            }
        }
        throw new IllegalArgumentException(
                "Unknown sort field '" + value + "'. Allowed: name, code, capital, continent, currency, phoneCode");
    }
}
