package com.ncba.rdas.cache;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Surfaces reference-data state in {@code /actuator/health}. Reports DOWN until the
 * first snapshot loads, then UP with the load time and dataset size — so an outage
 * that prevents the very first load is visible to Kubernetes probes and alerting,
 * while a transient refresh failure (serving stale data) keeps the service UP.
 */
@Component("referenceData")
public class ReferenceDataHealthIndicator implements HealthIndicator {

    private final ReferenceDataService referenceData;

    public ReferenceDataHealthIndicator(ReferenceDataService referenceData) {
        this.referenceData = referenceData;
    }

    @Override
    public Health health() {
        if (!referenceData.isLoaded()) {
            return Health.down()
                    .withDetail("reason", "reference-data snapshot not yet loaded")
                    .build();
        }
        ReferenceDataSnapshot s = referenceData.snapshot();
        return Health.up()
                .withDetail("loadedAt", s.loadedAt().toString())
                .withDetail("countries", s.countries().size())
                .withDetail("continents", s.continents().size())
                .withDetail("currencies", s.currencies().size())
                .build();
    }
}
