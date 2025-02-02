package org.nio.transaction.impl;


import com.google.protobuf.InvalidProtocolBufferException;
import com.nio.wallet.grpc.WalletServiceOuterClass.TransferRequest;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nio.config.QueueConfig;
import org.nio.endpoint.MapperKt;
import org.nio.logging.FailLogger;
import org.nio.sqs.QueuePublisher;
import org.nio.sqs.QueuePublisherKt;
import org.nio.transaction.FailedTransaction;
import org.nio.transaction.TransactionBufferQueue;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.*;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

import static java.util.concurrent.TimeUnit.SECONDS;

@Slf4j
@RequiredArgsConstructor
@Component
public class TransactionWorker {
    final TransactionServiceImpl transactionService;
    final ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor();
    final TransactionBufferQueue queue;
    final SqsAsyncClient sqsClient;

    @PostConstruct
    public void init() {
        executorService.submit(() -> {
            log.info("Worker started");
            AtomicLong start = new AtomicLong(System.currentTimeMillis());
            AtomicLong count = new AtomicLong();
            run().subscribe(batch -> {
                // debug
                count.getAndAdd(batch.successful().size());
                if (count.get() >= 1_000) {
                    var now = System.currentTimeMillis();
                    log.info("Process {} transaction in {} ms", count.get(), now - start.get());
                    start.set(now);
                    count.set(0);
                }
            });
        });
    }

    public Flux<DeleteMessageBatchResponse> run() {
        return queue.pollMessageFlux()
            .flatMap(groupFlux -> {
                return groupFlux
                    .concatMap(message -> {
                        MessageAttributeValue value = message.messageAttributes().get(QueuePublisherKt.MESSAGE_CONTENT);
                        TransferRequest request = null;
                        try {
                            request = TransferRequest.parseFrom(value.binaryValue().asByteArray());
                            request = MapperKt.newInstanceWithSpanId(request); // ReAssign spanId
                            log.debug("Received message: {} - {} {}", QueuePublisher.hashRingIndex(message), message.body(), request);
                            return transactionService.persistTransaction(request)
                                .thenReturn(message);
                        } catch (InvalidProtocolBufferException e) {
                            // TODO: handle parse fail request
                            log.error("Failed to parse message: {}", message);
                            FailLogger.appendFail(
                                new FailedTransaction(
                                    message.messageAttributes().get(QueuePublisherKt.TRACE_ID).stringValue(),
                                    message.messageAttributes().get(QueuePublisherKt.SPAN_ID).stringValue(),
                                    message.body(),
                                    e.toString()
                                )
                            );
                            return Mono.just(message);
                        } catch (NullPointerException e) {
                            log.error("Failed to parse message npe: {}", message);
                            FailLogger.appendFail(new FailedTransaction(
                                message.messageAttributes().get(QueuePublisherKt.TRACE_ID).stringValue(),
                                message.messageAttributes().get(QueuePublisherKt.SPAN_ID).stringValue(),
                                message.body(),
                                e.toString()
                            ));
                            return Mono.just(message);
                        }
                    })
                    .bufferTimeout(10, Duration.ofSeconds(1))
                    .flatMap(this::cleanMessageBatch);
            })
            .doOnNext(message -> log.debug("Processed message: {}", message.successful().size()));
//                .bufferTimeout(transactionConfig.getBufferSize(), transactionConfig.getBufferTime())
//                .doOnNext(this::cleanMessageBatch)

    }

    // Fix me
    public Mono<DeleteMessageBatchResponse> cleanMessageBatch(List<Message> batch) {
        var entries = batch.stream()
            .peek(message -> log.debug("Delete message: {}", message.messageId()))
            .map(message -> DeleteMessageBatchRequestEntry.builder()
                .id(message.messageId())
                .receiptHandle(message.receiptHandle())
                .build()).toList();
        var resp = sqsClient.deleteMessageBatch(DeleteMessageBatchRequest.builder()
            .queueUrl(QueueConfig.QUEUE_URL)
            .entries(entries)
            .build());
        return Mono.fromFuture(resp);
    }

    @PreDestroy
    public void destroy() throws InterruptedException {
        executorService.shutdown();
        executorService.awaitTermination(20, SECONDS);
    }

}
