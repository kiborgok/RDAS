package com.ncba.rdas.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Strongly-typed binding for all {@code rdas.*} configuration.
 */
@ConfigurationProperties(prefix = "rdas")
public class RdasProperties {

    private final Soap soap = new Soap();
    private final Cache cache = new Cache();
    private final Query query = new Query();

    public Soap getSoap() {
        return soap;
    }

    public Cache getCache() {
        return cache;
    }

    public Query getQuery() {
        return query;
    }

    public static class Soap {
        private String endpoint;
        private String namespace;
        private int connectTimeoutMs = 5000;
        private int readTimeoutMs = 15000;

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getNamespace() {
            return namespace;
        }

        public void setNamespace(String namespace) {
            this.namespace = namespace;
        }

        public int getConnectTimeoutMs() {
            return connectTimeoutMs;
        }

        public void setConnectTimeoutMs(int connectTimeoutMs) {
            this.connectTimeoutMs = connectTimeoutMs;
        }

        public int getReadTimeoutMs() {
            return readTimeoutMs;
        }

        public void setReadTimeoutMs(int readTimeoutMs) {
            this.readTimeoutMs = readTimeoutMs;
        }
    }

    public static class Cache {
        private Duration refreshInterval = Duration.ofHours(6);
        private boolean initialLoadOnStartup = true;
        private boolean serveStaleOnFailure = true;

        public Duration getRefreshInterval() {
            return refreshInterval;
        }

        public void setRefreshInterval(Duration refreshInterval) {
            this.refreshInterval = refreshInterval;
        }

        public boolean isInitialLoadOnStartup() {
            return initialLoadOnStartup;
        }

        public void setInitialLoadOnStartup(boolean initialLoadOnStartup) {
            this.initialLoadOnStartup = initialLoadOnStartup;
        }

        public boolean isServeStaleOnFailure() {
            return serveStaleOnFailure;
        }

        public void setServeStaleOnFailure(boolean serveStaleOnFailure) {
            this.serveStaleOnFailure = serveStaleOnFailure;
        }
    }

    public static class Query {
        private int defaultPageSize = 20;
        private int maxPageSize = 100;

        public int getDefaultPageSize() {
            return defaultPageSize;
        }

        public void setDefaultPageSize(int defaultPageSize) {
            this.defaultPageSize = defaultPageSize;
        }

        public int getMaxPageSize() {
            return maxPageSize;
        }

        public void setMaxPageSize(int maxPageSize) {
            this.maxPageSize = maxPageSize;
        }
    }
}
