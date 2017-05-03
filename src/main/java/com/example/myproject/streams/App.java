package com.example.myproject.streams;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.graphite.GraphiteReporter;
import com.example.myproject.metrics.PipelineGraphiteReporter;
import com.example.myproject.metrics.PipelineZabbixReporter;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.concurrent.*;

public abstract class App {
    protected static final Config config = ConfigFactory.load();
    protected static final Logger log = LogManager.getLogger(App.class);
    protected static volatile boolean shutdown = false;

    // metrics
    private GraphiteReporter graphiteReporter;
    private PipelineZabbixReporter zabbixReporter;
    protected final MetricRegistry metrics = new MetricRegistry();

    private static void addShutdownHook(App consumer) {
        Thread hook = new Thread(consumer::shutdownApp);
        Runtime.getRuntime().addShutdownHook(hook);
    }

    public abstract App start() throws InterruptedException;
    public abstract App shutdown();

    public void createReporters() {
        this.graphiteReporter = PipelineGraphiteReporter.build(metrics);
        this.zabbixReporter = PipelineZabbixReporter.build(metrics);
        addShutdownHook(this);
    }

    public String getMetricName(String name) {
        return String.format("application.%s", name);
    }

    public MetricRegistry getMetrics() {
        return metrics;
    }

    public void run() {
        log.info("Starting app");
        createReporters();
        try {
            // Start the metric reporters
            if (PipelineGraphiteReporter.enabled()) {
                log.info("Starting Graphite reporter");
                graphiteReporter.start(PipelineGraphiteReporter.getPollPeriod(), TimeUnit.SECONDS);
            }
            if (PipelineZabbixReporter.enabled()) {
                log.info("Starting Zabbix reporter");
                zabbixReporter.start(PipelineZabbixReporter.getPollPeriod(), TimeUnit.SECONDS);
            }
            // Start the stream
            start();
            while (!Thread.currentThread().isInterrupted() && !shutdown) {}
        }
        catch (InterruptedException e) {
            // No need to restore interrupt since consumer shuts down immediately.
            log.error("Thread interrupted while streaming");
        }
        catch (Throwable t) {
            // Uncaught exception
            log.error("Caught unchecked exception", t);
        }
        finally {
            shutdownApp();
        }
    }

    public void restart() {
        shutdownApp();
        try {
            Thread.sleep(15000L);
            run();
        } catch (InterruptedException e) {
            log.error("Thread interrupted while restarting");
        }
    }

    public void shutdownApp() {
        if (!shutdown) {
            log.info("Begin shutting down app");
            shutdown();
            // Stop the metric reporters
            if (PipelineGraphiteReporter.enabled()) {
                log.info("Shutting down Graphite reporter");
                graphiteReporter.stop();
            }
            if (PipelineZabbixReporter.enabled()) {
                log.info("Shutting down Zabbix reporter");
                zabbixReporter.stop();
            }
        }
        shutdown = true;
    }

    protected void runWithTimeout(String taskName, long timeout, TimeUnit unit, Runnable runnable) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future future = executor.submit(runnable);
        // This does not cancel the already-scheduled task
        executor.shutdown();
        try {
            future.get(timeout, unit);
        }
        catch (InterruptedException e) {
            log.error("Timeout task '{}' was interrupted", taskName, e);
        }
        catch (ExecutionException e) {
            log.error("Timeout task '{}' encountered an execution exception", taskName, e);
        }
        catch (TimeoutException e) {
            log.error("Timeout task '{}' exceeded timeout of {} {}",
                    taskName, timeout, unit.toString().toLowerCase(), e);
        }
        if (!executor.isTerminated()) {
            // Stop the code that hasn't finished
            executor.shutdownNow();
        }
    }

    protected String stackTraceAsString(Exception e) {
        StringWriter writer = new StringWriter();
        PrintWriter printWriter = new PrintWriter( writer );
        e.printStackTrace(printWriter);
        printWriter.flush();
        return writer.toString();
    }

}