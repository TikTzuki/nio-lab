package org.nio.transaction.impl;

import com.nio.wallet.grpc.WalletServiceOuterClass.TransferRequest;
import com.nio.wallet.grpc.WalletServiceOuterClass.TransferResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nio.account.AccountRepository;
import org.nio.logging.DeadLetterLogger;
import org.nio.logging.FailLogger;
import org.nio.sqs.QueuePublisher;
import org.nio.transaction.*;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionServiceImpl {
  final TransactionRepository repository;
  final AccountRepository accountRepository;
  final QueuePublisher publisher;

  /**
   * Always return Response
   *
   * @param request
   * @return
   */
  public Flux<TransferResponse> prepareTransfer(Flux<TransferRequest> request) {
    long start = System.currentTimeMillis();
    return request
      .bufferTimeout(10, Duration.ofMillis(1))
      .flatMap(batch -> publisher.publish(batch)
        .flux()
        .flatMap(it -> TransactionMappersKt.mapBatchResponse(batch, it))
        .onErrorResume(e -> {
          log.error(e.getMessage(), e);
          return Flux.fromIterable(batch).map(TransactionMappersKt::genericFail);
        }))
      .doOnComplete(() -> log.debug("Complete prepare transfer flux {} ms", System.currentTimeMillis() - start));
  }

  public Mono<NewTransaction> persistTransaction(TransferRequest request) {
    return updateBalance(request)
      .flatMap(this::insertTransaction);
  }

  Mono<TransferRequest> updateBalance(TransferRequest request) {
    var accountId = request.getUserId();
    var amount = new BigDecimal(request.getAmount());
    return
      accountRepository
        .getAccountBalance(accountId)
//            Mono.just(new AccountBalance(BigDecimal.valueOf(1000), 1))
        .switchIfEmpty(Mono.error(new RuntimeException("Account not found")))
        .doOnNext(balanceAndVersion -> {
          log.debug("Balance: {} {}", request, balanceAndVersion.balance());
          if (balanceAndVersion.balance().compareTo(amount) < 0)
            throw new InsufficientBalance(request.getReferenceId());
        })
        .onErrorResume(e -> true, e -> {
          FailLogger.appendFail(new FailedTransaction(
            request.getTraceId(),
            request.getSpanId(),
            request.getReferenceId(),
            e.toString()
          ));
          return Mono.empty();
        })

        .flatMap(balanceAndVersion -> accountRepository.updateBalance(
          accountId,
          balanceAndVersion.balance().subtract(amount),
          balanceAndVersion.version() + 1,
          balanceAndVersion.version()
        ))
//                .flatMap(it -> Mono.just(true))
//                .delayElement(Duration.ofMillis(new Random().nextInt(0, 300)))
        .mapNotNull(success -> {
          if (!success) {
            log.error("Update balance fail {} : stop mono", request);
            DeadLetterLogger.appendDeadLetter(FailedTransaction.fromTransferRequest(request, null));
            return null;
          }
          return request;
        })
        .doOnSuccess(success -> log.debug("Update balance success: {}", request));
  }

  Mono<NewTransaction> insertTransaction(TransferRequest request) {
    log.debug("Begin insert transaction: {}", request);
    var id = UUID.randomUUID().toString();
    var accountId = request.getUserId();
    var ticketId = request.getTicketId();
    var amount = new BigDecimal(request.getAmount());
    return repository.insertBatch(new Transaction(
        id,
        Instant.now(),
        accountId,
        ticketId,
        TransactionType.WITHDRAW,
        TransactionAction.BET,
        request.getReferenceId(),
        amount,
        BigDecimal.ZERO,
        1
      ))
//                .flatMap(it -> Mono.just(new NewTransaction(UUID.randomUUID().toString(), request.getReferenceId())))
      .doOnSuccess(newTran -> log.debug("Insert transaction success: {}", newTran))
      .doOnError(throwable -> {
        log.error("Insert transaction fail", throwable);
        DeadLetterLogger.appendDeadLetter(FailedTransaction.fromTransferRequest(request, throwable));
      })
      .onErrorResume(_ -> Mono.empty());
  }


}
