package org.nio.client;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Mono;
import reactor.core.publisher.ParallelFlux;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.logging.Level;

@Slf4j
public class ParallelListTest {

  <T> ParallelFlux<T> splitListIntoParallelFlux(List<T> list, int parallelism) {
    Hooks.onOperatorDebug();
    return Flux.fromIterable(list)
      .parallel(parallelism)
      .runOn(Schedulers.parallel())
      .log("cat1", Level.INFO)
      .flatMap(Mono::just);
  }

  @Test
  void test() {
    // Example usage
    var list = List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
    var parallelFlux = splitListIntoParallelFlux(list, 4);

    parallelFlux.subscribe(it -> log.info("Processed: {}", it));
  }

}
