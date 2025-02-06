package org.nio.reactive;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

public class TestThenReturn {
  @Test
  public void test() {
    int alwaysReturn = 99;
    int result = Mono.just(0)
      .flatMap(i -> {
        System.out.println("flatMap1");
        return Mono.just(1);
      })
      .doOnNext(it -> {
        throw new RuntimeException("test");
      })
      .onErrorResume(e -> true, e -> Mono.empty())
//                .flatMap(i -> {
//                    System.out.println("flatMap2");
//                    return Mono.just(2);
//                })
      .thenReturn(alwaysReturn)
      .block();
    assert result == alwaysReturn;
  }
}
