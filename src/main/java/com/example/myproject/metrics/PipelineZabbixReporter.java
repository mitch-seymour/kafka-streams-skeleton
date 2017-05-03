package com.example.myproject.metrics;

import com.codahale.metrics.*;
import com.example.myproject.config.ZabbixConfig;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.github.hengyunabc.zabbix.sender.DataObject;
import io.github.hengyunabc.zabbix.sender.SenderResult;
import io.github.hengyunabc.zabbix.sender.ZabbixSender;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.SortedMap;
import java.util.concurrent.TimeUnit;

/**
 * A reporter which publishes metric values to a Zabbix server.
 */
public class PipelineZabbixReporter extends ScheduledReporter {
    /**
     * Returns a new {@link Builder} for {@link PipelineZabbixReporter}.
     *
     * @param registry the registry to report
     * @return a {@link Builder} instance for a {@link PipelineZabbixReporter}
     */
    public static Builder forRegistry(MetricRegistry registry) {
        return new Builder(registry);
    }

    /**
     * A builder for {@link PipelineZabbixReporter} instances. Defaults to not using a prefix, using the
     * default clock, converting rates to events/second, converting durations to milliseconds, and
     * not filtering metrics.
     */
    public static class Builder {
        private final MetricRegistry registry;
        private Clock clock;
        private String prefix;
        private TimeUnit rateUnit;
        private TimeUnit durationUnit;
        private MetricFilter filter;

        private Builder(MetricRegistry registry) {
            this.registry = registry;
            this.clock = Clock.defaultClock();
            this.prefix = null;
            this.rateUnit = TimeUnit.SECONDS;
            this.durationUnit = TimeUnit.MILLISECONDS;
            this.filter = MetricFilter.ALL;
        }

        /**
         * Use the given {@link Clock} instance for the time.
         *
         * @param clock a {@link Clock} instance
         * @return {@code this}
         */
        public Builder withClock(Clock clock) {
            this.clock = clock;
            return this;
        }

        /**
         * Prefix all metric names with the given string.
         *
         * @param prefix the prefix for all metric names
         * @return {@code this}
         */
        public Builder prefixedWith(String prefix) {
            this.prefix = prefix;
            return this;
        }

        /**
         * Convert rates to the given time unit.
         *
         * @param rateUnit a unit of time
         * @return {@code this}
         */
        public Builder convertRatesTo(TimeUnit rateUnit) {
            this.rateUnit = rateUnit;
            return this;
        }

        /**
         * Convert durations to the given time unit.
         *
         * @param durationUnit a unit of time
         * @return {@code this}
         */
        public Builder convertDurationsTo(TimeUnit durationUnit) {
            this.durationUnit = durationUnit;
            return this;
        }

        /**
         * Only report metrics which match the given filter.
         *
         * @param filter a {@link MetricFilter}
         * @return {@code this}
         */
        public Builder filter(MetricFilter filter) {
            this.filter = filter;
            return this;
        }

        /**
         * Builds a {@link PipelineZabbixReporter} with the given properties
         *
         * @return a {@link PipelineZabbixReporter}
         */
        public PipelineZabbixReporter build() {
            return new PipelineZabbixReporter(registry,
                    clock,
                    prefix,
                    rateUnit,
                    durationUnit,
                    filter);
        }

    }

    public static PipelineZabbixReporter build(MetricRegistry metricRegistry) {
        return new Builder(metricRegistry)
                .convertRatesTo(TimeUnit.SECONDS)
                .convertDurationsTo(TimeUnit.MILLISECONDS)
                .filter(MetricFilter.ALL)
                .build();
    }

    private static final Config config = ConfigFactory.load();
    private static final Properties zabbixConfig = new ZabbixConfig().props(config);
    private static final boolean enabled =  Boolean.parseBoolean(zabbixConfig.getProperty(ZabbixConfig.REPORTING_ENABLED));
    private static final String host = zabbixConfig.getProperty(ZabbixConfig.ZABBIX_SERVER_HOST);
    private static final int pollPeriod = Integer.parseInt(zabbixConfig.getProperty(ZabbixConfig.POLL_PERIOD_SECONDS));
    private static final int port = Integer.parseInt(zabbixConfig.getProperty(ZabbixConfig.ZABBIX_SERVER_PORT));
    private static final String targetHost = zabbixConfig.getProperty(ZabbixConfig.ZABBIX_DATA_HOST);
    private static final ZabbixSender zabbixSender = new ZabbixSender(host, port);

    private static final Logger log = LogManager.getLogger(PipelineZabbixReporter.class);

    private final Clock clock;
    private final String prefix;

    private PipelineZabbixReporter(
            MetricRegistry registry,
            Clock clock,
            String prefix,
            TimeUnit rateUnit,
            TimeUnit durationUnit,
            MetricFilter filter) {
        super(registry, "zabbix-reporter", filter, rateUnit, durationUnit);
        this.clock = clock;
        this.prefix = prefix;
    }

    public static boolean enabled() {
        return enabled;
    }

    public static int getPollPeriod() {
        return pollPeriod;
    }

    @Override
    public void report(SortedMap<String, Gauge> gauges,
                       SortedMap<String, Counter> counters,
                       SortedMap<String, Histogram> histograms,
                       SortedMap<String, Meter> meters,
                       SortedMap<String, Timer> timers) {
        final long timestamp = clock.getTime() / 1000;

        try {

            for (Map.Entry<String, Gauge> entry : gauges.entrySet()) {
                reportGauge(entry.getKey(), entry.getValue(), timestamp);
            }

            for (Map.Entry<String, Counter> entry : counters.entrySet()) {
                reportCounter(entry.getKey(), entry.getValue(), timestamp);
            }

            for (Map.Entry<String, Histogram> entry : histograms.entrySet()) {
                reportHistogram(entry.getKey(), entry.getValue(), timestamp);
            }

            for (Map.Entry<String, Meter> entry : meters.entrySet()) {
                reportMetered(entry.getKey(), entry.getValue(), timestamp);
            }

            for (Map.Entry<String, Timer> entry : timers.entrySet()) {
                reportTimer(entry.getKey(), entry.getValue(), timestamp);
            }

        } catch (IOException e) {
            log.warn("Unable to report to Zabbix", e);
        }
    }

    private SenderResult send(String key, String value, long timestamp) throws IOException {
        DataObject dataObject = new DataObject();
        dataObject.setHost(targetHost);
        dataObject.setKey(key);
        dataObject.setValue(value);
        dataObject.setClock(timestamp);
        return zabbixSender.send(dataObject);
    }

    private void reportTimer(String name, Timer timer, long timestamp) throws IOException {
        final Snapshot snapshot = timer.getSnapshot();
        send(prefix(name, "max"), format(convertDuration(snapshot.getMax())), timestamp);
        send(prefix(name, "mean"), format(convertDuration(snapshot.getMean())), timestamp);
        send(prefix(name, "min"), format(convertDuration(snapshot.getMin())), timestamp);
        send(prefix(name, "stddev"), format(convertDuration(snapshot.getStdDev())), timestamp);
        send(prefix(name, "p50"), format(convertDuration(snapshot.getMedian())), timestamp);
        send(prefix(name, "p75"), format(convertDuration(snapshot.get75thPercentile())), timestamp);
        send(prefix(name, "p95"), format(convertDuration(snapshot.get95thPercentile())), timestamp);
        send(prefix(name, "p98"), format(convertDuration(snapshot.get98thPercentile())), timestamp);
        send(prefix(name, "p99"), format(convertDuration(snapshot.get99thPercentile())), timestamp);
        send(prefix(name, "p999"), format(convertDuration(snapshot.get999thPercentile())), timestamp);
        reportMetered(name, timer, timestamp);
    }

    private void reportMetered(String name, Metered meter, long timestamp) throws IOException {
        send(prefix(name, "count"), format(meter.getCount()), timestamp);
        send(prefix(name, "m1_rate"), format(convertRate(meter.getOneMinuteRate())), timestamp);
        send(prefix(name, "m5_rate"), format(convertRate(meter.getFiveMinuteRate())), timestamp);
        send(prefix(name, "m15_rate"), format(convertRate(meter.getFifteenMinuteRate())), timestamp);
        send(prefix(name, "mean_rate"), format(convertRate(meter.getMeanRate())), timestamp);
    }

    private void reportHistogram(String name, Histogram histogram, long timestamp) throws IOException {
        final Snapshot snapshot = histogram.getSnapshot();
        send(prefix(name, "count"), format(histogram.getCount()), timestamp);
        send(prefix(name, "max"), format(snapshot.getMax()), timestamp);
        send(prefix(name, "mean"), format(snapshot.getMean()), timestamp);
        send(prefix(name, "min"), format(snapshot.getMin()), timestamp);
        send(prefix(name, "stddev"), format(snapshot.getStdDev()), timestamp);
        send(prefix(name, "p50"), format(snapshot.getMedian()), timestamp);
        send(prefix(name, "p75"), format(snapshot.get75thPercentile()), timestamp);
        send(prefix(name, "p95"), format(snapshot.get95thPercentile()), timestamp);
        send(prefix(name, "p98"), format(snapshot.get98thPercentile()), timestamp);
        send(prefix(name, "p99"), format(snapshot.get99thPercentile()), timestamp);
        send(prefix(name, "p999"), format(snapshot.get999thPercentile()), timestamp);
    }

    private void reportCounter(String name, Counter counter, long timestamp) throws IOException {
        send(prefix(name, "count"), format(counter.getCount()), timestamp);
    }

    private void reportGauge(String name, Gauge gauge, long timestamp) throws IOException {
        final String value = format(gauge.getValue());
        if (value != null) {
            send(prefix(name), value, timestamp);
        }
    }

    private String format(Object o) {
        if (o instanceof Float) {
            return format(((Float) o).doubleValue());
        } else if (o instanceof Double) {
            return format(((Double) o).doubleValue());
        } else if (o instanceof Byte) {
            return format(((Byte) o).longValue());
        } else if (o instanceof Short) {
            return format(((Short) o).longValue());
        } else if (o instanceof Integer) {
            return format(((Integer) o).longValue());
        } else if (o instanceof Long) {
            return format(((Long) o).longValue());
        }
        return null;
    }

    private String prefix(String... components) {
        return MetricRegistry.name(prefix, components);
    }

    private String format(long n) {
        return Long.toString(n);
    }

    private String format(double v) {
        return String.format(Locale.US, "%2.2f", v);
    }
}

