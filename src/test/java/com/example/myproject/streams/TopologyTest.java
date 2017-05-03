package com.example.myproject.streams;

import com.example.myproject.config.KafkaStreamsConfig;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.kstream.KStreamBuilder;
import org.apache.kafka.streams.processor.AbstractProcessor;
import org.apache.kafka.streams.processor.Processor;
import org.apache.kafka.streams.processor.ProcessorContext;
import org.apache.kafka.streams.processor.ProcessorSupplier;
import org.apache.kafka.test.KStreamTestDriver;
import org.apache.kafka.test.ProcessorTopologyTestDriver;
import org.junit.Assert;

import java.util.ArrayList;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Properties;

import static org.apache.kafka.streams.StreamsConfig.CACHE_MAX_BYTES_BUFFERING_CONFIG;
import static org.apache.kafka.streams.StreamsConfig.ZOOKEEPER_CONNECT_CONFIG;
import static org.junit.Assert.fail;

public abstract class TopologyTest {

    private StreamsConfig streamsConfig = getStreamsConfig();
    protected StreamsApp stream = getStream(streamsConfig);
    protected MockProcessorSupplier processor = new MockProcessorSupplier();

    protected abstract StreamsApp getStream(StreamsConfig streamsConfig);

    protected class BeforeAfter {
        public KeyValue before;
        public ProducerRecord<String, String> after;

        BeforeAfter(KeyValue before, ProducerRecord<String, String> after) {
            this.before = before;
            this.after = after;
        }
    }

    // Not to be confused with the MockProcessorSupplier included in the org.apache.kafka.test package
    protected class MockProcessorSupplier<K, V> implements ProcessorSupplier<K, V> {
        public final ArrayList<KeyValue> processed;
        public final ArrayList<Long> punctuated;
        private final long scheduleInterval;

        public MockProcessorSupplier() {
            this(-1L);
        }

        public MockProcessorSupplier(long scheduleInterval) {
            this.processed = new ArrayList();
            this.punctuated = new ArrayList();
            this.scheduleInterval = scheduleInterval;
        }

        public Processor<K, V> get() {
            return new MockProcessorSupplier.MockProcessor();
        }

        public void checkAndClearProcessResult(KeyValue... expected) {
            Assert.assertEquals("the number of outputs:" + this.processed, (long) expected.length, (long) this.processed.size());

            for(int i = 0; i < expected.length; ++i) {
                Assert.assertEquals(expected[i].key, this.processed.get(i).key);
                Assert.assertEquals(expected[i].value, this.processed.get(i).value);
            }

            this.processed.clear();
        }

        public void checkEmptyAndClearProcessResult() {
            Assert.assertEquals("the number of outputs:", 0L, (long) this.processed.size());
            this.processed.clear();
        }

        public void checkAndClearPunctuateResult(long... expected) {
            Assert.assertEquals("the number of outputs:", (long) expected.length, (long) this.punctuated.size());

            for(int i = 0; i < expected.length; ++i) {
                Assert.assertEquals("output[" + i + "]:", expected[i], ((Long) this.punctuated.get(i)).longValue());
            }

            this.processed.clear();
        }

        public class MockProcessor extends AbstractProcessor<K, V> {
            public MockProcessor() {
            }

            public void init(ProcessorContext context) {
                super.init(context);
                if(MockProcessorSupplier.this.scheduleInterval > 0L) {
                    context.schedule(MockProcessorSupplier.this.scheduleInterval);
                }

            }

            public void process(K key, V value) {
                MockProcessorSupplier.this.processed.add(new KeyValue(key, value));
            }

            public void punctuate(long streamTime) {
                Assert.assertEquals(streamTime, this.context().timestamp());
                Assert.assertEquals(-1L, (long) this.context().partition());
                Assert.assertEquals(-1L, this.context().offset());
                MockProcessorSupplier.this.punctuated.add(Long.valueOf(streamTime));
            }
        }
    }

    public File createStateDir() {
        try {
            final File parentFile = new File("/tmp/kafka-streams");
            parentFile.mkdirs();
            final File file = Files.createTempDirectory(parentFile.toPath(), "kafka-misc-test-").toFile();
            file.deleteOnExit();
            return file;
        } catch (IOException e) {
            fail("Could not create state directory");
            return null;
        }
    }

    protected StreamsConfig getStreamsConfig() {
        Config config = ConfigFactory.load();
        Properties streamingProperties = new KafkaStreamsConfig().getStreamingProperties(config);
        streamingProperties.remove(ZOOKEEPER_CONNECT_CONFIG);
        streamingProperties.put(CACHE_MAX_BYTES_BUFFERING_CONFIG, 0);
        return new StreamsConfig(streamingProperties);
    }

    protected KStreamTestDriver getKStreamTestDriver() {
        return getKStreamTestDriver(createStateDir());
    }

    protected KStreamTestDriver getKStreamTestDriver(File stateDir) {
        KStreamBuilder builder = stream.getBuilder();
        builder.stream(stream.getDestinationTopic()).process(processor);
        KStreamTestDriver driver = new KStreamTestDriver(builder, stateDir);
        driver.setTime(0L);
        return driver;
    }

    protected ProcessorTopologyTestDriver getProcessorTopologyTestDriver(String... storeNames) {
        return new ProcessorTopologyTestDriver(streamsConfig, stream.getBuilder(), storeNames);
    }
}
