package com.example.myproject.config;

import java.util.Arrays;
import java.util.List;

public class ZabbixConfig extends LibraryConfig {

    public static final String POLL_PERIOD_SECONDS = "poll.period.seconds";
    public static final String REPORTING_ENABLED = "reporting.enabled";
    public static final String ZABBIX_DATA_HOST = "data.host";
    public static final String ZABBIX_SERVER_HOST = "host";
    public static final String ZABBIX_SERVER_PORT = "port";

    String prefix() {
        return "zabbix";
    }

    List<String> keys() {
        return Arrays.asList(
                POLL_PERIOD_SECONDS,
                REPORTING_ENABLED,
                ZABBIX_DATA_HOST,
                ZABBIX_SERVER_HOST,
                ZABBIX_SERVER_PORT
        );
    }
}
