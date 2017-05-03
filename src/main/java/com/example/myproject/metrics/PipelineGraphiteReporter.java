package com.example.myproject.metrics;

import com.codahale.metrics.graphite.GraphiteReporter;
import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.graphite.Graphite;
import com.example.myproject.config.GraphiteConfig;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import java.net.InetSocketAddress;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

public class PipelineGraphiteReporter {

    private static final Config config = ConfigFactory.load();
    private static final Properties graphiteConfig = new GraphiteConfig().props(config);
    private static final boolean enabled =  Boolean.parseBoolean(graphiteConfig.getProperty(GraphiteConfig.REPORTING_ENABLED));
    private static final int pollPeriod = Integer.parseInt(graphiteConfig.getProperty(GraphiteConfig.POLL_PERIOD_SECONDS));

    public static GraphiteReporter build(MetricRegistry metricRegistry) {
        InetSocketAddress carbonServer =
                new InetSocketAddress(
                        graphiteConfig.getProperty(GraphiteConfig.CARBON_HOST),
                        Integer.parseInt(graphiteConfig.getProperty(GraphiteConfig.CARBON_PORT)));

        return GraphiteReporter.forRegistry(metricRegistry)
                .prefixedWith(graphiteConfig.getProperty(GraphiteConfig.METRICS_PREFIX))
                .convertRatesTo(TimeUnit.SECONDS)
                .convertDurationsTo(TimeUnit.MILLISECONDS)
                .filter(MetricFilter.ALL)
                .build(new Graphite(carbonServer));
    }

    public static boolean enabled() {
        return enabled;
    }

    public static int getPollPeriod() {
        return pollPeriod;
    }

}
