package com.example.myproject.config;

import java.util.Arrays;
import java.util.List;

public class GraphiteConfig extends LibraryConfig {

    public static final String CARBON_HOST = "carbon.host";
    public static final String CARBON_PORT = "carbon.port";
    public static final String METRICS_PREFIX = "metrics.prefix";
    public static final String POLL_PERIOD_SECONDS = "poll.period.seconds";
    public static final String REPORTING_ENABLED = "reporting.enabled";

    String prefix() {
        return "graphite";
    }

    List<String> keys() {
        return Arrays.asList(
                CARBON_HOST,
                CARBON_PORT,
                METRICS_PREFIX,
                POLL_PERIOD_SECONDS,
                REPORTING_ENABLED
        );
    }
}
