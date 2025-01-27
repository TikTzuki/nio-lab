package org.nio.transaction.impl;

import com.nio.wallet.grpc.WalletServiceOuterClass;
import com.nio.wallet.grpc.WalletServiceOuterClass.TransferRequest;
import com.nio.wallet.grpc.WalletServiceOuterClass.TransferResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nio.account.AccountRepository;
import org.nio.logging.FailLogger;
import org.nio.sqs.MessageKt;
import org.nio.transaction.*;
import org.nio.logging.DeadLetterLogger;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchResponse;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionServiceImpl {
    final TransactionRepository repository;
    final AccountRepository accountRepository;
    final SqsClient sqsClient;

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
            .flatMap(batch -> {
                SendMessageBatchResponse response = MessageKt.publish(sqsClient, batch);
                return mapResponse(batch, response);
            })
            .doOnComplete(() -> log.info("Prepare complete: {}", System.currentTimeMillis() - start))
            .thenMany(Flux.empty());
    }
    Flux<TransferResponse> mapResponse(List<TransferRequest> batch, SendMessageBatchResponse response) {
        var batchMap= batch.stream().collect(Collectors.toMap(TransferRequest::getReferenceId, Function.identity()));
        return Flux.concat(
            Flux.fromIterable(response.failed()).map(resp->{
                var request = batchMap.get(resp.id());
                return TransferResponse.newBuilder()
                    .setCode(-1)
                    .setTraceId(request.getTraceId())
                    .setSpanId(request.getSpanId())
                    .setReferenceId(request.getReferenceId())
                    .build();
            }),
            Flux.fromIterable(response.successful()).map(resp->{
                var request = batchMap.get(resp.id());
                return TransferResponse.newBuilder()
                    .setCode(0)
                    .setTraceId(request.getTraceId())
                    .setSpanId(request.getSpanId())
                    .setReferenceId(request.getReferenceId())
                    .build();
            })
        );
    }

    public Mono<NewTransaction> persistTransaction(TransferRequest request) {
        return updateBalance(request)
            .flatMap(this::insertTransaction);
    }

    Mono<TransferRequest> updateBalance(TransferRequest request) {
        var accountId = request.getUserId();
        var amount = new BigDecimal(request.getAmount());
        return accountRepository
            .getAccountBalance(accountId)
            .doOnNext(balanceAndVersion -> {
                log.debug("Balance: {}", balanceAndVersion.balance());
                if (balanceAndVersion.balance().compareTo(amount) <= 0)
                    throw new InsufficientBalance(request.getReferenceId());
            })
            .onErrorResume(e -> true, e -> {
                FailLogger.appendFail(TransferRequest.newBuilder()
                    .setTraceId(request.getTraceId())
                    .setSpanId(request.getSpanId())
                    .setReferenceId(request.getReferenceId())
                    .build(), e);
                return Mono.empty();
            })
            .flatMap(balanceAndVersion -> accountRepository.updateBalance(
                accountId,
                balanceAndVersion.balance().subtract(amount),
                balanceAndVersion.version() + 1,
                balanceAndVersion.version()
            ))
            .flatMap(success -> {
                if (!success) {
                    log.error("Update balance fail {} : stop mono", request);
                    DeadLetterLogger.appendDeadLetter(request);
                    return Mono.empty();
                }
                return Mono.just(request);
            });
    }

    Mono<NewTransaction> insertTransaction(TransferRequest request) {
        log.debug("Update balance success: {}", request);
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
            .doOnError(throwable -> {
                log.error("Insert transaction fail", throwable);
                onInsertTransactionFail(request);
            })
            .onErrorResume(_ -> Mono.empty());
    }

    public void onInsertTransactionFail(TransferRequest request) {
        DeadLetterLogger.appendDeadLetter(request);
    }

}
