package com.example.myproject;

import com.example.myproject.streams.App;
import com.example.myproject.streams.StreamsApp;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KStreamBuilder;

public class HelloWorld extends StreamsApp {

    private final String sourceTopic = "test";
    private final String destinationTopic = "hello-filtered";

    public static void main(String[] args) {
        App consumer = new HelloWorld();
        consumer.run();
    }

    public HelloWorld() {
        super();
    }

    @Override
    public KStreamBuilder getBuilder() {
        log.info("Hello World");

        // Create a topology builder
        KStreamBuilder builder = new KStreamBuilder();

        KStream<String, byte[]> byteStream = builder.stream(Serdes.String(), Serdes.ByteArray(), sourceTopic);
        KStream<String, byte[]> filteredStream = byteStream.filter((key, msg) -> {
            log.info("Got a message! key = {}, value = {}", key, msg);
            return true;
        });

        // Push the messages to the compacted user_opt_out topic
        //filteredStream.to(Serdes.String(), Serdes.ByteArray(), destinationTopic);
        return builder;
    }

    @Override
    public String getDestinationTopic() {
        return destinationTopic;
    }
}
