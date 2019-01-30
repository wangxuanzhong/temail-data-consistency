package com.syswin.temail.data.consistency.mysql.stream;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.apache.curator.framework.CuratorFramework;

@Slf4j
public class AsyncZkBinlogSyncRecorder extends ZkBinlogSyncRecorder {

  private final ScheduledExecutorService scheduledExecutor = Executors.newSingleThreadScheduledExecutor();
  private final long updateIntervalMillis;
  private final AtomicBoolean updated = new AtomicBoolean();
  private volatile String binlogFilePosition;

  AsyncZkBinlogSyncRecorder(String clusterName, CuratorFramework curator, long updateIntervalMillis) {
    super(clusterName, curator);
    this.updateIntervalMillis = updateIntervalMillis;
  }

  @Override
  public void record(String filename, long position) {
    this.binlogFilePosition = filename + SEPARATOR + position;
    updated.set(true);
    log.debug("Saved binlog position [{}] locally", binlogFilePosition);
  }

  @Override
  public void flush() {
    flushIfUpdated();
    log.debug("Flushed binlog position [{}] to zookeeper", binlogFilePosition);
  }

  @Override
  void start() {
    super.start();
    scheduledExecutor.scheduleWithFixedDelay(this::flushIfUpdated, updateIntervalMillis, updateIntervalMillis, MILLISECONDS);
  }

  private void flushIfUpdated() {
    if (updated.compareAndSet(true, false)) {
      String[] strings = binlogFilePosition.split(SEPARATOR);
      updatePositionToZk(strings[0], Long.parseLong(strings[1]));
    }
  }

  @Override
  void shutdown() {
    super.shutdown();
    scheduledExecutor.shutdownNow();
  }
}