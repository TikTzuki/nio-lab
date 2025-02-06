package org.nio.config;

import com.datastax.oss.driver.api.core.ConsistencyLevel;

public class CassandraConfig {
  public static final ConsistencyLevel DEFAULT_WRITE_CONSISTENCY = ConsistencyLevel.LOCAL_QUORUM;
}
