package org.nio.account;

import reactor.core.publisher.Mono;

public interface AccountCustomRepository<T, ID> {
  Mono<ID> insertLite(T i);
}
