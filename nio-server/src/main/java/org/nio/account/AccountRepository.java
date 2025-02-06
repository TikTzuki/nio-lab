package org.nio.account;

import com.datastax.oss.driver.api.core.DefaultConsistencyLevel;
import org.springframework.data.cassandra.repository.Consistency;
import org.springframework.data.cassandra.repository.Query;
import org.springframework.data.cassandra.repository.ReactiveCassandraRepository;
import org.springframework.data.repository.query.Param;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

public interface AccountRepository extends ReactiveCassandraRepository<Account, String>, AccountCustomRepository<Account, String> {
  @Query("UPDATE account SET balance = :balance, version = :newVersion WHERE id = :id IF VERSION = :version")
  @Consistency(DefaultConsistencyLevel.LOCAL_QUORUM)
  Mono<Boolean> updateBalance(@Param("id") String id, @Param("balance") BigDecimal balance, @Param("newVersion") Long newVersion, @Param("version") Long version);

  @Query("SELECT balance, version FROM account WHERE id = :id")
  @Consistency(DefaultConsistencyLevel.LOCAL_ONE)
  Mono<AccountBalance> getAccountBalance(@Param("id") String id);
}
