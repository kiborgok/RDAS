package com.ncba.rdas.cache;

import com.ncba.rdas.domain.Country;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Immutable point-in-time view of all reference data, plus the lookup maps the
 * query layer needs. Swapped atomically on each successful refresh.
 *
 * @param countries        all countries, enriched with continent/currency names
 * @param countriesByCode  ISO code (upper-case) -> country, for O(1) detail lookups
 * @param continents       continent code -> name
 * @param currencies       currency code -> name
 * @param loadedAt         when this snapshot was built from the SOAP service
 */
public record ReferenceDataSnapshot(
        List<Country> countries,
        Map<String, Country> countriesByCode,
        Map<String, String> continents,
        Map<String, String> currencies,
        Instant loadedAt) {

    public ReferenceDataSnapshot {
        countries = List.copyOf(countries);
        countriesByCode = Map.copyOf(countriesByCode);
        continents = Map.copyOf(continents);
        currencies = Map.copyOf(currencies);
    }
}
