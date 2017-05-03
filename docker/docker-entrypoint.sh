#!/bin/bash

replace () {
  file_name=$1
  find_str=$(printf '%q' $2)
  replace_with=$(printf '%q' $3)
  sed -i -e "s#$find_str#$replace_with#g" $file_name
}

replace_with_quotes () {
  replace $1 $2 "\"$3\""
}

set -e

# Parse application config
replace $APP_CONFIG '@application.cleanup.onstart' $APP_CLEANUP_ON_START
replace $APP_CONFIG '@application.ignore.unrecognized.structs' $APP_IGNORE_UNRECOGNIZED_STRUCTS
replace $APP_CONFIG '@application.ktable.bootstrap' $APP_KTABLE_BOOTSTRAP
replace $APP_CONFIG '@graphite.carbon.port' $GRAPHITE_CARBON_PORT
replace $APP_CONFIG '@graphite.poll.period.seconds' $GRAPHITE_POLL_PERIOD_SECONDS
replace $APP_CONFIG '@graphite.reporting.enabled' $GRAPHITE_REPORTING_ENABLED
replace $APP_CONFIG '@kafka.consumer.fetch.max.wait.ms' $KAFKA_CONSUMER_FETCH_MAX_WAIT_MS
replace $APP_CONFIG '@kafka.consumer.partition.fetch.bytes' $KAFKA_CONSUMER_MAX_PARTITION_FETCH_BYTE
replace $APP_CONFIG '@kafka.consumer.max.poll.records' $KAFKA_CONSUMER_MAX_POLL_RECORDS
replace $APP_CONFIG '@kafka.streams.commit.interval.ms' $KAFKA_STREAMS_COMMIT_INTERVAL_MS
replace $APP_CONFIG '@kafka.streams.metrics.sample.window.ms' $KAFKA_STREAMS_METRICS_SAMPLE_WINDOW_MS
replace $APP_CONFIG '@kafka.streams.metrics.num.samples' $KAFKA_STREAMS_METRICS_NUM_SAMPLES
replace $APP_CONFIG '@kafka.streams.num.threads' $KAFKA_STREAMS_NUM_THREADS
replace $APP_CONFIG '@kafka.streams.poll.ms' $KAFKA_STREAMS_POLL_MS
replace $APP_CONFIG '@kafka.streams.request.timeout' $KAFKA_STREAMS_REQUEST_TIMEOUT
replace $APP_CONFIG '@kafka.streams.standby.replicas' $KAFKA_STREAMS_STANDBY_REPLICAS
replace $APP_CONFIG '@rocksdb.reporting.enabled' $ROCKSDB_REPORTING_ENABLED
replace $APP_CONFIG '@zabbix.port' $ZABBIX_PORT
replace $APP_CONFIG '@zabbix.poll.period.seconds' $ZABBIX_POLL_PERIOD_SECONDS
replace $APP_CONFIG '@zabbix.reporting.enabled' $ZABBIX_REPORTING_ENABLED

replace_with_quotes $APP_CONFIG '@dc' $DC
replace_with_quotes $APP_CONFIG '@graphite.carbon.host' $GRAPHITE_CARBON_HOST
replace_with_quotes $APP_CONFIG '@graphite.metric.prefix' "direct.${STATSD_NAMESPACE:-testing.myproject.$SHARD}"
replace_with_quotes $APP_CONFIG '@kafka.producer.acks' $KAFKA_PRODUCER_ACKS
replace_with_quotes $APP_CONFIG '@kafka.producer.compression.type' $KAFKA_PRODUCER_COMPRESSION_TYPE
replace_with_quotes $APP_CONFIG '@kafka.streams.application.id' $KAFKA_STREAMS_APPLICATION_ID
replace_with_quotes $APP_CONFIG '@kafka.streams.bootstrap.servers' $KAFKA_STREAMS_BOOTSTRAP_SERVERS
replace_with_quotes $APP_CONFIG '@kafka.streams.key.serde' $KAFKA_STREAMS_KEY_SERDE
replace_with_quotes $APP_CONFIG '@kafka.streams.state.dir' $KAFKA_STREAMS_STATE_DIR
replace_with_quotes $APP_CONFIG '@kafka.streams.value.serde' $KAFKA_STREAMS_VALUE_SERDE
replace_with_quotes $APP_CONFIG '@kafka.streams.zookeeper' $KAFKA_STREAMS_ZK_CONNECT
replace_with_quotes $APP_CONFIG '@shard' $SHARD
replace_with_quotes $APP_CONFIG '@zabbix.data.host' $ZABBIX_DATA_HOST
replace_with_quotes $APP_CONFIG '@zabbix.host' $ZABBIX_HOST

# Parse logging config
replace $LOG_CONFIG '@app.log.level' $APP_LOG_LEVEL
replace $LOG_CONFIG '@root.log.level' $ROOT_LOG_LEVEL

export JAVA_OPTS=$JAVA_OPTS

exec "$@"
