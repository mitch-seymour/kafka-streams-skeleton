package com.example.myproject.metrics;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.MetricRegistry;
import com.example.myproject.streams.StreamsApp;
import org.apache.kafka.streams.errors.InvalidStateStoreException;
import org.apache.kafka.streams.state.QueryableStoreTypes;
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

public class RocksDbMetricsReporter {
    private final Logger log = LogManager.getLogger(RocksDbMetricsReporter.class);
    private final MetricRegistry metrics;
    private final StreamsApp streamsApp;
    private static List<String> taskIds = new ArrayList<>();

    /**
     * Constructor
     * @param streamsApp
     */
    public RocksDbMetricsReporter(StreamsApp streamsApp) {
        this.metrics = streamsApp.getMetrics();
        this.streamsApp = streamsApp;
    }

    public void registerMetrics() {
        List<String> stateStoreNames = streamsApp.getStateStoreNames();
        if (stateStoreNames.size() == 0) {
            return;
        }
        for (String stateStoreName : stateStoreNames) {
            String metricName = String.format("rocksdb.%s.%s", stateStoreName, "estimate-num-keys");
            if (metrics.getNames().contains(metricName)) {
                metrics.remove(metricName);
            }
            metrics.register(metricName, new Gauge<Long>() {
                @Override
                public Long getValue() {
                    return getEstimatedNumKeysForStateStore(stateStoreName);
                }
            });
        }
    }

    public Long getEstimatedNumKeysForStateStore(String name) {
        try {
            ReadOnlyKeyValueStore<String, Long> store =
                    streamsApp.getKafkaStreamsInstance().store(name, QueryableStoreTypes.<String, Long>keyValueStore());
            return store.approximateNumEntries();
        } catch (InvalidStateStoreException e) {
            return 0L;
        }
    }
}
