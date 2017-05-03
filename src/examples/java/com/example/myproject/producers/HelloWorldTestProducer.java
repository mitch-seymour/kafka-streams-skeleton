package com.example.myproject.producers;

import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.thrift.TSerializer;
import org.apache.thrift.protocol.TBinaryProtocol;

import java.util.Properties;

import static org.apache.kafka.clients.producer.ProducerConfig.*;
import static org.apache.kafka.streams.StreamsConfig.ZOOKEEPER_CONNECT_CONFIG;

public class HelloWorldTestProducer extends TestProducer {

    private final KafkaProducer producer = getTestProducer();
    private final TSerializer serializer = new TSerializer(new TBinaryProtocol.Factory());

    public static void main(String[] args) {
        HelloWorldTestProducer producer = new HelloWorldTestProducer();
        producer.run();
    }

    @Override
    protected void sendRecord(int i) {
        ProducerRecord<String, byte[]> record = getRecord(random().nextLong(10000L, 1000000L));
        producer.send(record, new Callback() {
            public void onCompletion(RecordMetadata metadata, Exception e) {
                if (e != null) {
                    System.out.format("Failed to produce record. %s\n", record);
                    throw new RuntimeException(e);
                }
                System.out.format("Record sent. %s\n", record);
            }
        });
    }

    public ProducerRecord<String, byte[]> getRecord(long id) {
        return new ProducerRecord<>("test", String.format("random_key_%d", id), String.valueOf(id).getBytes());
    }

    public static KafkaProducer getTestProducer() {
        Properties config = new Properties();
        config.put(CLIENT_ID_CONFIG, "hello-world-test-producer");
        config.put(BOOTSTRAP_SERVERS_CONFIG, System.getProperty("kafka.streams.bootstrap.servers"));
        config.put(ZOOKEEPER_CONNECT_CONFIG, System.getProperty("kafka.streams.zookeeper.connect"));
        config.put(ACKS_CONFIG, "all");
        config.put(KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
        config.put(MAX_BLOCK_MS_CONFIG, 5000);
        System.out.format("Config:\n%s\n", config);
        return new KafkaProducer<String, String>(config);
    }

}
