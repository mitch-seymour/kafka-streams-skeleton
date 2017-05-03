package com.example.myproject.config;

import static org.apache.kafka.streams.StreamsConfig.*;
import static org.apache.kafka.clients.consumer.ConsumerConfig.AUTO_OFFSET_RESET_CONFIG;
import static org.apache.kafka.clients.consumer.ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG;
import static org.apache.kafka.clients.consumer.ConsumerConfig.MAX_PARTITION_FETCH_BYTES_CONFIG;
import static org.apache.kafka.clients.consumer.ConsumerConfig.MAX_POLL_RECORDS_CONFIG;
import static org.apache.kafka.clients.CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG;
import static org.apache.kafka.clients.producer.ProducerConfig.ACKS_CONFIG;
import static org.apache.kafka.clients.producer.ProducerConfig.COMPRESSION_TYPE_CONFIG;

import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import com.example.myproject.metrics.PipelineMetricsReporter;
import com.typesafe.config.Config;
import org.apache.kafka.streams.StreamsConfig;

public class KafkaStreamsConfig extends LibraryConfig {

    String prefix() {
        return "kafka.streams";
    }

    List<String> keys() {
        return Arrays.asList(
                ACKS_CONFIG,
                APPLICATION_ID_CONFIG,
                AUTO_OFFSET_RESET_CONFIG,
                BOOTSTRAP_SERVERS_CONFIG,
                COMMIT_INTERVAL_MS_CONFIG,
                COMPRESSION_TYPE_CONFIG,
                FETCH_MAX_WAIT_MS_CONFIG,
                KEY_SERDE_CLASS_CONFIG,
                VALUE_SERDE_CLASS_CONFIG,
                MAX_PARTITION_FETCH_BYTES_CONFIG,
                MAX_POLL_RECORDS_CONFIG,
                METRICS_NUM_SAMPLES_CONFIG,
                METRICS_SAMPLE_WINDOW_MS_CONFIG,
                NUM_STANDBY_REPLICAS_CONFIG,
                NUM_STREAM_THREADS_CONFIG,
                POLL_MS_CONFIG,
                STATE_DIR_CONFIG,
                TIMESTAMP_EXTRACTOR_CLASS_CONFIG,
                ZOOKEEPER_CONNECT_CONFIG
        );
    }

    public StreamsConfig fromConfig(Config parentConfig) {
        return new StreamsConfig(getStreamingProperties(parentConfig));
    }

    public Properties getStreamingProperties(Config parentConfig) {
        Properties streamingProps = props(parentConfig);
        streamingProps.put(METRIC_REPORTER_CLASSES_CONFIG, PipelineMetricsReporter.class.getName());
        if (parentConfig.getBoolean("interactive.queries.remote.enabled")) {
            String appServer = String.format("%s:%d",
                    parentConfig.getString("interactive.queries.host"),
                    parentConfig.getInt("interactive.queries.port"));
            streamingProps.put(APPLICATION_SERVER_CONFIG, appServer);
        }
        return streamingProps;
    }

}
