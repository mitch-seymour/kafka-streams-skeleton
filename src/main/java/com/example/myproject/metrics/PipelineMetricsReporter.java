package com.example.myproject.metrics;

import static java.lang.String.format;

import org.apache.kafka.common.MetricName;
import org.apache.kafka.common.metrics.KafkaMetric;
import org.apache.kafka.common.metrics.MetricsReporter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PipelineMetricsReporter implements MetricsReporter {

    private static Map<MetricName, KafkaMetric> metrics = new HashMap<>();
    private static ArrayList<String> monitorMetrics = new ArrayList<String>();

    /**
     * Configure this class with the given key-value pairs
     */
    public void configure(Map<String, ?> configs) {
        // We don't currently need any of the Kafka configuration values for our custom reporter
    }

    /**
     * This is called when the reporter is first registered to initially register all existing metrics
     * @param metrics All currently existing metrics
     */
    public void init(List<KafkaMetric> metrics) {
       registerMonitorableMetrics();
    }

    /**
     * This is called whenever a metric is updated or added
     * @param metric
     */
    public void metricChange(KafkaMetric metric) {
        MetricName metricName = metric.metricName();
        if (metrics.containsKey(metricName)) {
            // remove the old KafkaMetric instance
            metrics.remove(metricName);
        }
        if (monitorMetrics.contains(format("%s.%s", metricName.group(), metricName.name()))) {
            metrics.put(metricName, metric);
        }
    }

    /**
     * This is called whenever a metric is removed
     * @param metric
     */
    public void metricRemoval(KafkaMetric metric) {
        metrics.remove(metric.metricName());
    }

    /**
     * Called when the metrics repository is closed.
     */
    public void close() {
        metrics = new HashMap<>();
    }

    /**
     * Get the KafkaMetrics that have been registered
     * @return
     */
    public static Map<MetricName, KafkaMetric>  metrics() {
        return metrics;
    }

    /**
     * We only care about a subset of the many metrics that Kafka maintains. This method registers the metrics
     * we are interested in
     */
    public void registerMonitorableMetrics() {
        // The average number of bytes consumed per second
        monitorMetrics.add("consumer-fetch-manager-metrics.bytes-consumed-rate");

        // The average time taken for a fetch request
        monitorMetrics.add("consumer-fetch-manager-metrics.fetch-latency-avg");

        // The number of fetch requests per second
        monitorMetrics.add("consumer-fetch-manager-metrics.fetch-rate");

        // The average number of bytes fetched per request
        monitorMetrics.add("consumer-fetch-manager-metrics.fetch-size-avg");

        // The average number of records consumed per second
        monitorMetrics.add("consumer-fetch-manager-metrics.records-consumed-rate");

        // The current number of active connections.
        monitorMetrics.add("consumer-metrics.connection-count");

        // The average length of time the I/O thread spent waiting for a socket ready for reads or writes in nanoseconds
        monitorMetrics.add("consumer-metrics.io-wait-time-ns-avg");

        // The average number of outgoing bytes sent per second to all servers
        monitorMetrics.add("consumer-metrics.outgoing-byte-rate");

        // The current number of active connections.
        monitorMetrics.add("producer-metrics.connection-count");

        // Bytes/second read off all sockets.
        monitorMetrics.add("producer-metrics.incoming-byte-rate");

        // The average length of time for I/O per select call in nanoseconds.
        monitorMetrics.add("producer-metrics.io-wait-time-ns-avg");

        // The average number of outgoing bytes sent per second to all servers.
        monitorMetrics.add("producer-metrics.outgoing-byte-rate");

        // The average number of records sent per second
        monitorMetrics.add("producer-metrics.record-send-rate");

        // The average record size
        monitorMetrics.add("producer-metrics.record-size-avg");

        // The average request latency in ms
        monitorMetrics.add("producer-metrics.request-latency-avg");

        // Responses received sent per second
        monitorMetrics.add("producer-metrics.response-rate");

        // The average per-second number of commit calls
        monitorMetrics.add("stream-metrics.commit-calls-rate");

        // The average commit time in ms
        monitorMetrics.add("stream-metrics.commit-time-avg");

        // The average per-second number of record-poll calls
        monitorMetrics.add("stream-metrics.poll-calls-rate");

        // The average poll time in ms
        monitorMetrics.add("stream-metrics.poll-time-avg");

        // The maximum poll time in ms
        monitorMetrics.add("stream-metrics.poll-time-max");

        // The average per-second number of process calls
        monitorMetrics.add("stream-metrics.process-calls-rate");

        // The average process time in ms
        monitorMetrics.add("stream-metrics.process-time-avg-ms");

        // The maximum process time in ms
        monitorMetrics.add("stream-metrics.process-time-max-ms");

        // The average per-second number of newly created tasks
        monitorMetrics.add("stream-metrics.task-creation-rate");

        // The average per-second number of destructed tasks
        monitorMetrics.add("stream-metrics.task-destruction-rate");
    }

}
