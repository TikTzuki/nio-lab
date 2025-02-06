package org.nio.transaction;

import reactor.core.publisher.Mono;

public interface TransactionCustomRepository {
  Mono<NewTransaction> insertBatch(Transaction transaction);
}
