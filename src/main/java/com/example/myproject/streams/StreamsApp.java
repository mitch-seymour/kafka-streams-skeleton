package com.example.myproject.streams;

import static java.lang.String.format;
import static org.apache.kafka.clients.consumer.ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG;
import static org.apache.kafka.clients.consumer.ConsumerConfig.GROUP_ID_CONFIG;
import static org.apache.kafka.clients.consumer.ConsumerConfig.MAX_POLL_RECORDS_CONFIG;
import static org.apache.kafka.streams.StreamsConfig.APPLICATION_ID_CONFIG;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.example.myproject.metrics.PipelineMetricsReporter;
import com.example.myproject.metrics.RocksDbMetricsReporter;
import com.example.myproject.servers.InteractiveQueryServer;
import com.example.myproject.config.KafkaStreamsConfig;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.common.errors.InconsistentGroupProtocolException;
import org.apache.kafka.common.MetricName;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.InterruptException;
import org.apache.kafka.common.metrics.KafkaMetric;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.streams.KafkaClientSupplier;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.kstream.KStreamBuilder;
import org.apache.kafka.streams.processor.*;
import org.apache.thrift.TBaseProcessor;


import java.util.*;
import java.util.concurrent.TimeUnit;

public abstract class StreamsApp extends App {
    private KafkaStreams kafkaStreams;
    private TopologyBuilder topology;
    protected final StreamsConfig streamsConfig;

    // metrics
    protected final RocksDbMetricsReporter rocksDbReporter;
    protected final boolean rocksDbReportingEnabled = config.getBoolean("rocksdb.reporting.enabled");
    private final Counter consumersConstructed = metrics.counter(getMetricName("consumers-constructed"));
    private final Counter consumersRestored = metrics.counter(getMetricName("consumers-restored"));
    private final Counter producersConstructed = metrics.counter(getMetricName("producers-constructed"));

    // interactive query server
    Optional<InteractiveQueryServer> interactiveQueryServer = Optional.empty();

    // abstract
    protected abstract KStreamBuilder getBuilder();
    public abstract String getDestinationTopic();

    /**
     * Constructor with custom config
     * @param streamsConfig
     */
    protected StreamsApp(StreamsConfig streamsConfig) {
        this.streamsConfig = streamsConfig;
        this.rocksDbReporter = new RocksDbMetricsReporter(this);
    }

    /**
     * Constructor with default config
     */
    protected StreamsApp() {
        this.streamsConfig = new KafkaStreamsConfig().fromConfig(config);
        this.rocksDbReporter = new RocksDbMetricsReporter(this);
    }

    /**
     * Add Kafka metrics to the metric registry
     * @param kafkaMetrics the metrics to start tracking
     */
    private void addMetrics(Map<MetricName, KafkaMetric> kafkaMetrics) {
        Set<MetricName> metricKeys = kafkaMetrics.keySet();
        for (MetricName key : metricKeys) {
            KafkaMetric metric = kafkaMetrics.get(key);
            String metricName = getMetricName(metric.metricName());
            if (metrics.getNames().contains(metricName)) {
                metrics.remove(metricName);
            }
            metrics.register(metricName, new Gauge<Double>() {
                @Override
                public Double getValue() {
                    return metric.value();
                }
            });
        }
    }

    /**
     * We use our own client supplier so we can track when consumers/producers are constructed
     * or restored
     */
    private class ClientSupplier implements KafkaClientSupplier {
        @Override
        public Producer<byte[], byte[]> getProducer(Map<String, Object> config) {
            producersConstructed.inc();
            log.info("Creating producer {}: {}", producersConstructed.getCount(), config);
            return new KafkaProducer<>(config, new ByteArraySerializer(), new ByteArraySerializer());
        }

        @Override
        public Consumer<byte[], byte[]> getConsumer(Map<String, Object> config) {
            consumersConstructed.inc();
            log.info("Creating consumer {}: {}", consumersConstructed.getCount(), config);
            return new KafkaConsumer<>(config, new ByteArrayDeserializer(), new ByteArrayDeserializer());
        }

        @Override
        public Consumer<byte[], byte[]> getRestoreConsumer(Map<String, Object> config) {
            // Note: this consumer is used to consume internal topics
            consumersRestored.inc();
            return new KafkaConsumer<>(config, new ByteArrayDeserializer(), new ByteArrayDeserializer());
        }
    }

    /**
     * Get the topology that contains the logic for our streams app
     * @return
     */
    TopologyBuilder getTopology() {
        return topology;
    }

    /**
     * Creates meters for the custom metrics, and defines the topology for the KafkaStreams app
     */
    protected void initialize() {
        // Get the topology builder
        KStreamBuilder builder = getBuilder();
        // Build the app
        kafkaStreams = new KafkaStreams(builder, streamsConfig, new ClientSupplier());
        // Shutdown the entire app if an uncaught exception is encountered
        kafkaStreams.setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                log.error("Stream terminated because of uncaught exception: {}. Shutting down app", e.getMessage());
                String s = stackTraceAsString((Exception) e);
                log.error("Stacktrace: {}", s);
                System.out.println(e);
                shutdownApp();
            }
        });
        topology = builder;
        addMetrics(PipelineMetricsReporter.metrics());
    }

    /**
     * Child classes can override this if they support interactive queries
     * @return
     */
    protected Optional<TBaseProcessor> getInteractiveQueryProcessor() {
        return Optional.empty();
    }

    /**
     * Create an interactive query server
     * @param port
     * @param processor
     * @return a stopped instance of an interactive query server
     */
    private InteractiveQueryServer createInteractiveQueryServer(int port, TBaseProcessor processor) {
        interactiveQueryServer = Optional.of(new InteractiveQueryServer(port, processor));
        return interactiveQueryServer.get();
    }

    /**
     * Create and start and interactive query server
     * @param port
     * @param processor
     * @return a running instance of an interactive query server
     */
    private InteractiveQueryServer startInteractiveQueryServer(int port, TBaseProcessor processor) {
        return createInteractiveQueryServer(port, processor).start();
    }

    /**
     * Get a formatted metric name that includes the Kafka metric group
     * @param metricName
     * @return the formatted metric name
     */
    protected String getMetricName(MetricName metricName) {
        return format("%s.%s", metricName.group(), metricName.name());
    }

    /**
     * Get the KafkaStreams instance being used by this app
     * @return the KafkaStreams instance
     */
    public KafkaStreams getKafkaStreamsInstance() {
        return kafkaStreams;
    }

    /**
     * Get state store names. Child classes can optionally override this
     * @return
     */
    public List<String> getStateStoreNames() {
        return new ArrayList<>();
    }

    /**
     * Get a copy of the streams config that was used to build
     * this streams app
     * @return
     */
    public Map<String, Object> getStreamsConfig() {
        // returns a copy of the original properties
        return streamsConfig.originals();
    }

    /**
     * If a state store has not been initialized when streaming a KTable, force initialization. To understand why
     * this is necessary, please see KAFKA-4113
     * @param sourceTopic
     * @param keyDeserializer
     * @param valueDeserializer
     */
    public void forceKTableBootstrap(String sourceTopic, Deserializer keyDeserializer, Deserializer valueDeserializer) {
        try {
            Map<String, Object> config = getStreamsConfig();
            String groupId = config.get(APPLICATION_ID_CONFIG).toString();
            config.put(GROUP_ID_CONFIG, groupId);
            config.put(ENABLE_AUTO_COMMIT_CONFIG, true);
            config.put(MAX_POLL_RECORDS_CONFIG, 1);
            KafkaConsumer<String, String> consumer = new KafkaConsumer<>(config, keyDeserializer, valueDeserializer);
            ConsumerRebalanceListener listener = new ConsumerRebalanceListener() {
                public void onPartitionsRevoked(Collection<TopicPartition> partitions) {
                }

                public void onPartitionsAssigned(Collection<TopicPartition> partitions) {
                    List<TopicPartition> unInitialized = new ArrayList<>();
                    for (TopicPartition tp : partitions) {
                        OffsetAndMetadata offsetAndMetaData = consumer.committed(tp);
                        if (offsetAndMetaData == null) {
                            unInitialized.add(tp);
                        }
                    }
                    if (unInitialized.size() > 0) {
                        log.info("Bootstrapping {} state stores for topic: {}", unInitialized.size(), sourceTopic);
                        consumer.seekToBeginning(unInitialized);
                    } else {
                        log.info("{} state stores have already been bootstrapped for topic: {}", partitions.size(), sourceTopic);
                    }
                }
            };
            consumer.subscribe(Collections.singletonList(sourceTopic), listener);
            consumer.poll(1000L);
            consumer.close();
        } catch (InconsistentGroupProtocolException e) {
            log.info("Inconsistent group protocol while bootstrapping KTable. Restarting");
            restart();
        }
    }

    /**
     * Restart the application
     */
    @Override
    public void restart() {
        producersConstructed.dec(producersConstructed.getCount());
        consumersConstructed.dec(consumersConstructed.getCount());
        consumersRestored.dec(consumersRestored.getCount());
        super.restart();
    }

    /**
     * Start streaming
     * @return
     * @throws InterruptedException
     */
    @Override
    public StreamsApp start() {
        initialize();
        // see if we should reset the local state stores
        if (config.getBoolean("application.cleanup.onstart")) {
            log.info("Resetting the local state store");
            kafkaStreams.cleanUp();
        }
        // start the kafka streams instance
        log.info("Starting Kafka Streams threads");
        kafkaStreams.start();
        // initialize the RocksDb reporter if enabled
        if (rocksDbReportingEnabled) {
            rocksDbReporter.registerMetrics();
        }
        // start any interactive query servers that may be configured
        if (config.getBoolean("interactive.queries.remote.enabled")) {
            Optional<TBaseProcessor> processor = getInteractiveQueryProcessor();
            if (processor.isPresent()) {
                startInteractiveQueryServer(config.getInt("interactive.queries.port"), processor.get());
            }
        }
        return this;
    }

    /**
     * Stop streaming
     * @return
     */
    @Override
    public StreamsApp shutdown() {
        try {
            if (kafkaStreams != null) {
                log.info("Begin closing Kafka Streams threads");
                runWithTimeout("Closing streams threads", 20, TimeUnit.SECONDS, new Runnable() {
                    @Override
                    public void run() {
                        kafkaStreams.close();
                    }
                });
                log.info("Finished closing Kafka Streams threads");
            } else {
                Thread.currentThread().interrupt();
            }
            // shutdown the interactive query servers if there are any
            if (interactiveQueryServer.isPresent()) {
                log.info("Shutting down interactive query server");
                interactiveQueryServer.get().stop();
            }
        } catch (InterruptException e) {
            log.info("Thread interrupted while closing Kafka Streams threads");
        }
        return this;
    }
}
