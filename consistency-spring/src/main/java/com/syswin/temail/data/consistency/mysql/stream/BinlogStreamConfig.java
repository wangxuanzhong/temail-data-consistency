package com.syswin.temail.data.consistency.mysql.stream;

import static com.github.shyiko.mysql.binlog.event.EventType.EXT_WRITE_ROWS;
import static com.github.shyiko.mysql.binlog.event.EventType.TABLE_MAP;
import static com.syswin.temail.data.consistency.mysql.stream.DataSyncFeature.BINLOG;

import com.github.shyiko.mysql.binlog.event.Event;
import com.github.shyiko.mysql.binlog.event.EventType;
import com.syswin.library.database.event.stream.StatefulTaskSupplier;
import com.syswin.library.stateful.task.runner.ScheduledStatefulTask;
import com.syswin.temail.data.consistency.application.MQProducer;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.togglz.core.manager.FeatureManager;

@Configuration
class BinlogStreamConfig {

  @Bean
  EventHandler eventHandler(MQProducer mqProducer,
      FeatureManager featureManager,
      @Value("${app.consistency.binlog.rocketmq.retry.limit:3}") int maxRetries,
      @Value("${app.consistency.binlog.rocketmq.retry.interval:1000}") long retryIntervalMillis) {
    return new MqEventSender(new ToggleMqProducer(featureManager, BINLOG, mqProducer), maxRetries, retryIntervalMillis);
  }

  @Bean
  EventType[] eventTypes() {
    return new EventType[]{TABLE_MAP, EXT_WRITE_ROWS};
  }

  @Bean
  StatefulTaskSupplier housekeepingTaskSupplier(
      @Value("${app.consistency.binlog.housekeeper.sweep.limit:1000}") int limit,
      @Value("${app.consistency.binlog.housekeeper.sweep.interval:2000}") long sweepInterval,
      FeatureManager featureManager) {

    return dataSource -> new ScheduledStatefulTask(new ToggleRunnable(featureManager, BINLOG, new EventHousekeeper(dataSource, limit)), sweepInterval);
  }

  @Bean
  Function<DataSource, Consumer<Event>> mysqlEventHandlerSupplier(
      @Value("${app.consistency.binlog.mysql.tables:listener_event}") String[] tableNames,
      EventHandler eventHandler) {
    return dataSource -> new MysqlEventHandler(eventHandler, tableNames);
  }
}
