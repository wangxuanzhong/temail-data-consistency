package com.syswin.temail.data.consistency.mysql.stream;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.apache.curator.framework.recipes.leader.LeaderLatch.State.CLOSED;

import java.io.EOFException;
import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import lombok.extern.slf4j.Slf4j;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.leader.LeaderLatch;

@Slf4j
class ZkBasedStatefulTaskRunner {

  private static final String LEADER_LATCH_PATH = "/syswin/temail/binlog_stream/leader";
  private final CuratorFramework curator;
  private final LeaderLatch leaderLatch;
  private final ThreadPoolExecutor executor = singleTaskExecutor();

  private final StatefulTask task;

  ZkBasedStatefulTaskRunner(String participantId, StatefulTask task, CuratorFramework curator) {
    this.task = task;

    this.curator = curator;
    leaderLatch = new LeaderLatch(this.curator, LEADER_LATCH_PATH, participantId);
  }

  void start() throws Exception {
    curator.create().orSetData().creatingParentsIfNeeded().forPath(LEADER_LATCH_PATH);

    leaderLatch.start();
    curator.getConnectionStateListenable().addListener((client, newState) -> {
      if (newState.isConnected()) {
        log.info("Participant {} is connected to zookeeper {}",
            leaderLatch.getId(),
            curator.getZookeeperClient().getCurrentConnectionString());

        run();
      } else {
        log.error("Participant {} is disconnected from zookeeper {}",
            leaderLatch.getId(),
            curator.getZookeeperClient().getCurrentConnectionString());

        this.task.stop();
      }
    });
    run();
  }

  void run() {
    executor.execute(() -> {
      try {
        log.info("Participant {} is waiting for leadership", leaderLatch.getId());
        leaderLatch.await();
        log.info("Participant {} is running with leadership", leaderLatch.getId());
        task.start();
      } catch (InterruptedException | EOFException e) {
        log.warn("Failed to acquire leadership due to interruption", e);
      }
    });
  }

  void shutdown() {
    task.stop();
    executor.shutdownNow();
    try {
      if (!CLOSED.equals(leaderLatch.getState())) {
        leaderLatch.close();
      }
    } catch (IOException e) {
      log.warn("Failed to close leader latch of participant {}", leaderLatch.getId(), e);
    }
  }

  long taskCount() {
    return executor.getQueue().size();
  }

  boolean isLeader() {
    return leaderLatch.hasLeadership();
  }

  private ThreadPoolExecutor singleTaskExecutor() {
    return new ThreadPoolExecutor(1,
        1,
        0L,
        MILLISECONDS,
        new ArrayBlockingQueue<>(2), // up to 2 tasks to avoid frequent reconnect
        new ThreadPoolExecutor.DiscardPolicy());
  }
}
