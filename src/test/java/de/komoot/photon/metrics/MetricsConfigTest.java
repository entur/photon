package de.komoot.photon.metrics;

import de.komoot.photon.CommandLineArgs;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MetricsConfigTest {
    @Test
    void testInit() {
        String customPath = "/custom-metrics";

        CommandLineArgs args = new CommandLineArgs() {
            @Override
            public boolean getMetricsEnable() {
                return true;
            }

            @Override
            public String getMetricsPath() {
                return customPath;
            }
        };

        MetricsConfig metricsConfig = MetricsConfig.setupMetrics(args);
        assertNotNull(metricsConfig.getRegistry());
        assertNotNull(metricsConfig.getPlugin());
        assertEquals(customPath, metricsConfig.getPath());
        assertTrue(metricsConfig.isEnabled());
    }

    @Test
    void testNoInit() {
        CommandLineArgs args = new CommandLineArgs() {
            @Override
            public boolean getMetricsEnable() {
                return false;
            }
        };

        MetricsConfig metricsConfig = MetricsConfig.setupMetrics(args);
        assertThrows(IllegalStateException.class, metricsConfig::getRegistry);
        assertThrows(IllegalStateException.class, metricsConfig::getPlugin);
        assertFalse(metricsConfig.isEnabled());
    }

}