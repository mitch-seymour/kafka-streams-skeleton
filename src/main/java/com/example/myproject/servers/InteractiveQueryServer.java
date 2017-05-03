package com.example.myproject.servers;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.thrift.TBaseProcessor;
import org.apache.thrift.server.TServer;
import org.apache.thrift.server.TSimpleServer;
import org.apache.thrift.transport.TServerSocket;
import org.apache.thrift.transport.TServerTransport;
import org.apache.thrift.transport.TTransportException;

public class InteractiveQueryServer {
    private static final Logger log = LogManager.getLogger(InteractiveQueryServer.class);
    private final Thread thread;

    private TServer createServer(TServerTransport serverTransport, TBaseProcessor processor) {
        return new TSimpleServer(new TServer.Args(serverTransport).processor(processor));
    }

    public InteractiveQueryServer(int port, TBaseProcessor processor) {
        Runnable simple = new Runnable() {
            @Override
            public void run() {
                try {
                    log.info("Starting the interactive query server on port: {}", port);
                    TServerTransport serverTransport = new TServerSocket(port);
                    createServer(serverTransport, processor).serve();
                } catch (TTransportException e) {
                    log.error(e);
                }
            }
        };
        this.thread = new Thread(simple);
    }

    public InteractiveQueryServer start() {
        thread.start();
        return this;
    }

    public InteractiveQueryServer stop() {
        thread.interrupt();
        return this;
    }
}
