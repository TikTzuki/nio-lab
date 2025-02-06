package org.nio.account.impl;

import lombok.RequiredArgsConstructor;
import org.nio.account.Account;
import org.nio.account.AccountCustomRepository;
import org.springframework.data.cassandra.core.ReactiveCassandraTemplate;
import org.springframework.data.cassandra.core.cql.WriteOptions;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class AccountCustomRepositoryImpl implements AccountCustomRepository<Account, String> {
  final ReactiveCassandraTemplate template;

  @Override
  public Mono<String> insertLite(Account i) {
    var statement = template.getStatementFactory().insert(i, WriteOptions.builder()
      .build()).build();
    return template.execute(statement)
      .mapNotNull(r -> i.getId());
  }
}
