package com.ncba.rdas.domain;

import java.util.List;

/**
 * Canonical, enriched country record held in the in-memory snapshot.
 *
 * <p>Continent and currency names are resolved from their reference lists at load
 * time so that downstream callers never need a second lookup.
 *
 * @param isoCode       ISO country code (e.g. {@code KE})
 * @param name          country name (e.g. {@code Kenya})
 * @param capitalCity   capital city
 * @param phoneCode     international dialing code
 * @param continentCode continent code (e.g. {@code AF})
 * @param continentName resolved continent name (e.g. {@code Africa})
 * @param currencyCode  currency ISO code (e.g. {@code KES})
 * @param currencyName  resolved currency name (e.g. {@code Shilling})
 * @param flagUrl       URL of the country flag
 * @param languages     languages spoken in the country
 */
public record Country(
        String isoCode,
        String name,
        String capitalCity,
        String phoneCode,
        String continentCode,
        String continentName,
        String currencyCode,
        String currencyName,
        String flagUrl,
        List<Language> languages) {

    public Country {
        languages = languages == null ? List.of() : List.copyOf(languages);
    }
}
