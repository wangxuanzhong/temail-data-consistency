package com.syswin.temail.data.consistency.mysql.stream;

import static com.seanyinx.github.unit.scaffolding.Randomness.nextLong;
import static com.seanyinx.github.unit.scaffolding.Randomness.uniquify;
import static org.assertj.core.api.Assertions.assertThat;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.curator.test.TestingServer;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class ZkBinlogSyncRecorderTest {
  CuratorFramework curator;
  private static TestingServer zookeeper;

  final String clusterName = uniquify("cluster");
  final String filename = uniquify("filename");
  final long position = nextLong();

  ZkBinlogSyncRecorder recorder;

  @BeforeClass
  public static void beforeClass() throws Exception {
    zookeeper = new TestingServer(2181, true);
  }

  @AfterClass
  public static void afterClass() throws Exception {
    zookeeper.close();
  }

  @Before
  public void setUp() throws Exception {
    curator = CuratorFrameworkFactory.newClient(
        zookeeper.getConnectString(),
        new ExponentialBackoffRetry(1000, Integer.MAX_VALUE));
    curator.start();
    curator.blockUntilConnected();
  }

  @After
  public void tearDown() {
    recorder.shutdown();
    curator.close();
  }

  @Test(expected = IllegalStateException.class)
  public void blowsUpWhenGettingFileNameIfNotConnectedToZookeeper() {
    curator.close();
    recorder.filename();
  }

  @Test (expected = IllegalStateException.class)
  public void blowsUpWhenGettingPositionIfNotConnectedToZookeeper() {
    curator.close();
    recorder.position();
  }

  @Test
  public void nullFileNameWhenNoDataOnZookeeper() {
    assertThat(recorder.filename()).isNull();
  }

  @Test
  public void zeroPositionWhenNoDataOnZookeeper() {
    assertThat(recorder.position()).isZero();
  }
}
