package com.example.myproject.producers;

import java.util.concurrent.ThreadLocalRandom;

public abstract class TestProducer {

    private final ThreadLocalRandom random = ThreadLocalRandom.current();

    protected abstract void sendRecord(int i);

    protected void run() {
        int i = 0;
        int currentIteration = 0;
        int maxIterations = Integer.valueOf(System.getProperty("test.producer.iterations", "1"));
        int recordsPerIteration = Integer.valueOf(System.getProperty("test.producer.iteration.size", "10")) - 1;
        long delay = Long.valueOf(System.getProperty("test.producer.delay", "1000"));
        while(!Thread.currentThread().isInterrupted()){
            try {
                i++;
                sendRecord(i);
                Thread.sleep(delay);
                if (i > recordsPerIteration) {
                    // Enforce the iteration cap. These test producers are not meant to run indefinitely
                    currentIteration += 1;
                    System.out.format("Completed iteration %s\n\n", currentIteration);
                    if (currentIteration == maxIterations) {
                        break;
                    }
                    i = 0;
                }
            } catch (InterruptedException e) {
                System.out.format("Test Producer interrupted\n");
                break;
            }
        }
    }

    protected ThreadLocalRandom random() {
        return random;
    }
}
