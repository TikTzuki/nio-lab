package org.nio.transaction.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.nio.account.AccountBalance;
import org.nio.account.AccountRepository;
import org.nio.config.TransactionConfig;
import org.nio.sqs.QueuePublisherKt;
import org.nio.transaction.*;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalTime;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.quality.Strictness.LENIENT;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = LENIENT)
public class TransactionWorkerTest {
  @Mock
  SqsAsyncClient client;
  @Mock
  TransactionConfig config;
  @Mock
  TransactionRepository transactionRepository;
  @Mock
  AccountRepository accountRepository;
  TransactionBufferQueue queue;
  TransactionWorker worker;
  TransactionServiceImpl transactionService;

  @BeforeEach
  void setup() {
    transactionService = new TransactionServiceImpl(transactionRepository, accountRepository, null);
    queue = new TransactionBufferQueue(client, config);
    worker = new TransactionWorker(transactionService, queue);
  }

  @Test
  void testRun() throws InterruptedException {
    // given
    given(config.getReceiveMessageWaitTime()).willReturn(Duration.ofSeconds(20));
    given(config.getNumberOfMessages()).willReturn(10);
    var allMessages = TestTransactionUtils.generateMessages(List.of("u1", "u2", "u3", "u4"), 5);
    given(client.receiveMessage(any(ReceiveMessageRequest.class)))
      .willAnswer(it -> {
        var messages = Stream.generate(allMessages::poll)
          .limit(10)
          .filter(Objects::nonNull)
          .toList();
        ReceiveMessageResponse response = ReceiveMessageResponse.builder()
          .messages(messages.isEmpty() ? null : messages)
          .build();
        return completedFuture(response);
      });
    given(accountRepository.getAccountBalance(anyString()))
      .willReturn(Mono.just(new AccountBalance(BigDecimal.valueOf(100), 0L)));
    given(accountRepository.updateBalance(
      anyString(),
      any(BigDecimal.class),
      anyLong(),
      anyLong()))
      .willReturn(Mono.just(true));
    given(transactionRepository.insertBatch(any()))
      .willAnswer(it -> {
          var tran = it.getArgument(0, Transaction.class);
          Thread.sleep(new Random().nextInt(0, 300));
          return Mono.just(List.of(new NewTransaction(tran.getId(), tran.getRefId())));
        }
      );
    CountDownLatch latch = new CountDownLatch(1);
    // when
    worker.run()
      .map(it -> {
        System.out.println(it);
        return it;
      })
      .doOnComplete(latch::countDown)
      .subscribe(message -> {
        var hash = message.messageAttributes().get(QueuePublisherKt.HASH_RING_INDEX).stringValue();
        var logContent = "%s [%s]: %s - %s".formatted(LocalTime.now(), Thread.currentThread().getName(), message.body(), hash);
        System.out.println(logContent);
      });

    latch.await(5, TimeUnit.SECONDS);
  }

}
