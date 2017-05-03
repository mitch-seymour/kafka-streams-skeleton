package com.example.myproject.config;

import com.typesafe.config.Config;

import java.util.List;
import java.util.Properties;

abstract class LibraryConfig {

    public Properties props(Config parentConfig) {
        Config config = parentConfig.getConfig(prefix());
        Properties props = new Properties();
        keys().forEach(key -> props.put(key, config.getString(key)));
        return props;
    }

    abstract String prefix();

    abstract List<String> keys();
}
