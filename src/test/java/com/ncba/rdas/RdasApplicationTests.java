package com.ncba.rdas;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * Verifies the Spring context wires up. Startup SOAP load is disabled so the test
 * makes no network calls; the scheduled refresh has a 6h initial delay and does not
 * fire during the test.
 */
@SpringBootTest
@TestPropertySource(properties = "rdas.cache.initial-load-on-startup=false")
class RdasApplicationTests {

    @Test
    void contextLoads() {
    }
}
