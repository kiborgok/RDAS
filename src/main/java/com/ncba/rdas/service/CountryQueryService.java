package com.ncba.rdas.service;

import com.ncba.rdas.cache.ReferenceDataService;
import com.ncba.rdas.cache.ReferenceDataSnapshot;
import com.ncba.rdas.config.CacheConfig;
import com.ncba.rdas.domain.Country;
import com.ncba.rdas.exception.CountryNotFoundException;
import com.ncba.rdas.web.dto.CountryResponse;
import com.ncba.rdas.web.dto.PageResponse;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * Applies filtering, sorting and pagination over the in-memory snapshot.
 *
 * <p>Because all data is already in memory, every query is a cheap stream pipeline;
 * results are additionally memoised via {@link Cacheable} keyed by the full criteria,
 * so repeated identical queries (common from dashboards/partners) skip recomputation.
 */
@Service
public class CountryQueryService {

    private final ReferenceDataService referenceData;

    public CountryQueryService(ReferenceDataService referenceData) {
        this.referenceData = referenceData;
    }

    /** Search/list countries with filtering, sorting and pagination. */
    @Cacheable(cacheNames = CacheConfig.COUNTRY_QUERY_CACHE)
    public PageResponse<CountryResponse> search(CountrySearchCriteria c) {
        ReferenceDataSnapshot snapshot = referenceData.snapshot();

        List<Country> matched = snapshot.countries().stream()
                .filter(country -> matchesName(country, c.name()))
                .filter(country -> matchesContinent(country, c.continent()))
                .filter(country -> matchesCurrency(country, c.currency()))
                .filter(country -> matchesLanguage(country, c.language()))
                .sorted(direction(c))
                .toList();

        return paginate(matched, c.page(), c.size(), describeSort(c));
    }

    /** All countries that use a given currency (ISO code or name) — paginated. */
    public PageResponse<CountryResponse> countriesByCurrency(
            String currency, int page, int size, SortField sortField, boolean ascending) {
        CountrySearchCriteria c = CountrySearchCriteria.normalised(
                null, null, currency, null, page, size, sortField, ascending);
        return search(c);
    }

    /** Full detail for a single country by ISO code. */
    public CountryResponse getByCode(String code) {
        ReferenceDataSnapshot snapshot = referenceData.snapshot();
        Country country = snapshot.countriesByCode().get(code.trim().toUpperCase(Locale.ROOT));
        if (country == null) {
            throw new CountryNotFoundException(code);
        }
        return CountryResponse.from(country);
    }

    // ---- filters -------------------------------------------------------------

    private static boolean matchesName(Country c, String name) {
        return name == null || containsIgnoreCase(c.name(), name);
    }

    private static boolean matchesContinent(Country c, String continent) {
        return continent == null
                || equalsIgnoreCase(c.continentCode(), continent)
                || containsIgnoreCase(c.continentName(), continent);
    }

    private static boolean matchesCurrency(Country c, String currency) {
        return currency == null
                || equalsIgnoreCase(c.currencyCode(), currency)
                || containsIgnoreCase(c.currencyName(), currency);
    }

    private static boolean matchesLanguage(Country c, String language) {
        if (language == null) {
            return true;
        }
        return c.languages().stream().anyMatch(l ->
                equalsIgnoreCase(l.isoCode(), language) || containsIgnoreCase(l.name(), language));
    }

    // ---- helpers -------------------------------------------------------------

    private static Comparator<Country> direction(CountrySearchCriteria c) {
        Comparator<Country> cmp = c.sortField().comparator();
        return c.ascending() ? cmp : cmp.reversed();
    }

    private static String describeSort(CountrySearchCriteria c) {
        return c.sortField().apiName() + "," + (c.ascending() ? "asc" : "desc");
    }

    private static PageResponse<CountryResponse> paginate(
            List<Country> matched, int page, int size, String sort) {
        long total = matched.size();
        int from = Math.min(page * size, matched.size());
        int to = Math.min(from + size, matched.size());
        List<CountryResponse> content = matched.subList(from, to).stream()
                .map(CountryResponse::from)
                .toList();
        return PageResponse.of(content, page, size, total, sort);
    }

    private static boolean containsIgnoreCase(String haystack, String needle) {
        return haystack != null
                && haystack.toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT));
    }

    private static boolean equalsIgnoreCase(String a, String b) {
        return a != null && a.equalsIgnoreCase(b);
    }
}
