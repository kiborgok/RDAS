package com.ncba.rdas.service;

/**
 * Normalised, validated query criteria. Used as a cache key, so it is a record
 * (value-based equality) and all string fields are pre-normalised by
 * {@link #normalised}.
 *
 * @param name      partial, case-insensitive country-name match (nullable)
 * @param continent continent code or name (nullable)
 * @param currency  currency ISO code or name (nullable)
 * @param language  language ISO code or name (nullable)
 * @param page      zero-based page index
 * @param size      page size
 * @param sortField field to sort by
 * @param ascending sort direction
 */
public record CountrySearchCriteria(
        String name,
        String continent,
        String currency,
        String language,
        int page,
        int size,
        SortField sortField,
        boolean ascending) {

    public static CountrySearchCriteria normalised(
            String name, String continent, String currency, String language,
            int page, int size, SortField sortField, boolean ascending) {
        return new CountrySearchCriteria(
                trimToNull(name), trimToNull(continent), trimToNull(currency), trimToNull(language),
                page, size, sortField, ascending);
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
