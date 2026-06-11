package com.ncba.rdas.cache;

import com.ncba.rdas.config.CacheConfig;
import com.ncba.rdas.config.RdasProperties;
import com.ncba.rdas.domain.Country;
import com.ncba.rdas.domain.Language;
import com.ncba.rdas.exception.ReferenceDataUnavailableException;
import com.ncba.rdas.soap.CountryInfoSoapClient;
import com.ncba.rdas.soap.dto.CodeNameListEnvelope.CodeName;
import com.ncba.rdas.soap.dto.FullCountryInfoEnvelope.SoapCountryInfo;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Owns the in-memory reference-data snapshot and keeps it fresh.
 *
 * <p><b>Why this design.</b> The dataset (~246 countries) is small and changes
 * very rarely, while every channel queries it constantly. Rather than calling SOAP
 * per request, RDAS loads the whole dataset in a handful of SOAP calls, holds it in
 * memory, and serves all user queries from there. This:
 * <ul>
 *   <li>reduces SOAP traffic to ~3 calls per refresh interval (far below the
 *       100 req/min provider limit), and</li>
 *   <li>provides resilience: if a refresh fails, the previous good snapshot keeps
 *       serving traffic (configurable), so a SOAP outage is invisible to users.</li>
 * </ul>
 *
 * <p>The snapshot is swapped atomically, so readers always see a consistent view.
 */
@Service
public class ReferenceDataService {

    private static final Logger log = LoggerFactory.getLogger(ReferenceDataService.class);

    private final CountryInfoSoapClient soapClient;
    private final RdasProperties props;
    private final CacheManager cacheManager;
    private final AtomicReference<ReferenceDataSnapshot> snapshotRef = new AtomicReference<>();

    public ReferenceDataService(CountryInfoSoapClient soapClient, RdasProperties props,
            CacheManager cacheManager) {
        this.soapClient = soapClient;
        this.props = props;
        this.cacheManager = cacheManager;
    }

    /** Warm the cache once the application context is ready (non-fatal on failure). */
    @EventListener(ApplicationReadyEvent.class)
    public void warmUpOnStartup() {
        if (!props.getCache().isInitialLoadOnStartup()) {
            return;
        }
        try {
            refresh();
        } catch (RuntimeException e) {
            // Do not crash the app: it can serve traffic once SOAP recovers and the
            // scheduled refresh succeeds. Health probes will report DOWN meanwhile.
            log.error("Initial reference-data load failed; will retry on schedule. Cause: {}",
                    e.getMessage());
        }
    }

    /** Scheduled full refresh. Interval is driven by {@code rdas.cache.refresh-interval}. */
    @Scheduled(fixedRateString = "${rdas.cache.refresh-interval}",
            initialDelayString = "${rdas.cache.refresh-interval}")
    public void scheduledRefresh() {
        try {
            refresh();
        } catch (RuntimeException e) {
            if (props.getCache().isServeStaleOnFailure() && snapshotRef.get() != null) {
                log.warn("Scheduled refresh failed; continuing to serve the previous snapshot "
                        + "(loaded {}). Cause: {}", snapshotRef.get().loadedAt(), e.getMessage());
            } else {
                log.error("Scheduled refresh failed and no usable snapshot is available: {}",
                        e.getMessage());
            }
        }
    }

    /**
     * Rebuilds the snapshot from the SOAP service. Builds into local variables and
     * only swaps the live reference once every call succeeded, so a partial failure
     * never corrupts the served data.
     */
    public void refresh() {
        log.info("Refreshing reference data from CountryInfo SOAP service...");
        Map<String, String> continents = toCodeNameMap(soapClient.fetchContinents());
        Map<String, String> currencies = toCodeNameMap(soapClient.fetchCurrencies());
        List<SoapCountryInfo> raw = soapClient.fetchAllCountries();

        List<Country> countries = new ArrayList<>(raw.size());
        Map<String, Country> byCode = new LinkedHashMap<>(raw.size() * 2);
        for (SoapCountryInfo c : raw) {
            if (c.isoCode == null) {
                continue;
            }
            Country country = new Country(
                    c.isoCode,
                    c.name,
                    c.capitalCity,
                    c.phoneCode,
                    c.continentCode,
                    continents.getOrDefault(c.continentCode, c.continentCode),
                    c.currencyIsoCode,
                    currencies.getOrDefault(c.currencyIsoCode, c.currencyIsoCode),
                    c.countryFlag,
                    toLanguages(c));
            countries.add(country);
            byCode.put(c.isoCode.toUpperCase(), country);
        }

        snapshotRef.set(new ReferenceDataSnapshot(countries, byCode, continents, currencies, Instant.now()));
        evictQueryCache();
        log.info("Reference data refreshed: {} countries, {} continents, {} currencies",
                countries.size(), continents.size(), currencies.size());
    }

    /** @return the current snapshot, or fail fast with 503 if never loaded. */
    public ReferenceDataSnapshot snapshot() {
        ReferenceDataSnapshot snapshot = snapshotRef.get();
        if (snapshot == null) {
            throw new ReferenceDataUnavailableException(
                    "Reference data is not yet available; the upstream service may be unreachable. "
                            + "Please retry shortly.");
        }
        return snapshot;
    }

    /** @return true once at least one snapshot has been loaded (used by health checks). */
    public boolean isLoaded() {
        return snapshotRef.get() != null;
    }

    /** @return when the live snapshot was loaded, or {@code null} if never loaded. */
    public Instant loadedAt() {
        ReferenceDataSnapshot s = snapshotRef.get();
        return s == null ? null : s.loadedAt();
    }

    /** Drop memoised query results so the new snapshot is reflected immediately. */
    private void evictQueryCache() {
        Cache cache = cacheManager.getCache(CacheConfig.COUNTRY_QUERY_CACHE);
        if (cache != null) {
            cache.clear();
        }
    }

    private static List<Language> toLanguages(SoapCountryInfo c) {
        if (c.languages == null) {
            return List.of();
        }
        List<Language> langs = new ArrayList<>(c.languages.size());
        c.languages.forEach(l -> langs.add(new Language(l.isoCode, l.name)));
        return langs;
    }

    private static Map<String, String> toCodeNameMap(List<CodeName> list) {
        Map<String, String> map = new LinkedHashMap<>();
        for (CodeName cn : list) {
            if (cn.code() != null) {
                map.put(cn.code(), cn.name);
            }
        }
        return map;
    }
}
