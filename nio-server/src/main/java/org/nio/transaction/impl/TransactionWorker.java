package org.nio.transaction.impl;


import com.google.protobuf.InvalidProtocolBufferException;
import com.nio.wallet.grpc.WalletServiceOuterClass.TransferRequest;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nio.config.QueueConfig;
import org.nio.config.TransactionConfig;
import org.nio.endpoint.MapperKt;
import org.nio.logging.FailLogger;
import org.nio.sqs.MessageKt;
import org.nio.transaction.FailedTransaction;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SynchronousSink;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.*;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;

import static java.util.concurrent.TimeUnit.SECONDS;

@Slf4j
@RequiredArgsConstructor
@Component
public class TransactionWorker {
    final SqsAsyncClient sqsClient;
    final TransactionServiceImpl transactionService;
    final ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor();
    final TransactionConfig transactionConfig;

    @PostConstruct
    public void init() {
        ReceiveMessageRequest receiveMessageRequest = ReceiveMessageRequest.builder()
            .queueUrl(QueueConfig.QUEUE_URL)
            .messageAttributeNames(MessageKt.MESSAGE_CONTENT, MessageKt.TRACE_ID, MessageKt.SPAN_ID)
            .waitTimeSeconds((int) transactionConfig.getReceiveMessageWaitTime().toSeconds())
            .maxNumberOfMessages(transactionConfig.getNumberOfMessages())
            .build();

        executorService.submit(() -> {
            log.info("Worker started");

            Callable<Long> initState = () -> 0L;
            BiFunction<Long, SynchronousSink<Long>, Long> infinityMessageGenerator = (i, sink) -> {
                sink.next(i);
                return i;
            };
            AtomicLong start = new AtomicLong(System.currentTimeMillis());
            AtomicLong count = new AtomicLong();
            Flux.generate(initState, infinityMessageGenerator)
                .concatMap(i -> {
                    return Mono.fromFuture(sqsClient.receiveMessage(receiveMessageRequest)); // Block generator until has message
                })
                .concatMap(sqsMessages -> {
                    log.debug("Poll {} messages", sqsMessages.messages().size());
                    return Flux.fromIterable(sqsMessages.messages());
                })
                .concatMap(message -> {
                    MessageAttributeValue value = message.messageAttributes().get(MessageKt.MESSAGE_CONTENT);
                    TransferRequest request = null;
                    try {
                        request = TransferRequest.parseFrom(value.binaryValue().asByteArray());
                        request = MapperKt.newInstanceWithSpanId(request); // ReAssign spanId
                        log.debug("Received message: {} {}", message, request);
                        return transactionService.persistTransaction(request).thenReturn(message);
                    } catch (InvalidProtocolBufferException e) {
                        // TODO: handle parse fail request
                        log.error("Failed to parse message: {}", message);
                        FailLogger.appendFail(
                            new FailedTransaction(
                                message.messageAttributes().get(MessageKt.TRACE_ID).stringValue(),
                                message.messageAttributes().get(MessageKt.SPAN_ID).stringValue(),
                                message.body(),
                                e.toString()
                            )
                        );
                        return Mono.just(message);
                    } catch (NullPointerException e) {
                        log.error("Failed to parse message npe: {}", message);
                        FailLogger.appendFail(new FailedTransaction(
                            message.messageAttributes().get(MessageKt.TRACE_ID).stringValue(),
                            message.messageAttributes().get(MessageKt.SPAN_ID).stringValue(),
                            message.body(),
                            e.toString()
                        ));
                        return Mono.just(message);
                    }
                })
                .bufferTimeout(transactionConfig.getBufferSize(), transactionConfig.getBufferTime())
                .doOnNext(this::cleanMessageBatch)
                .subscribe(result -> {
                    start.set(System.currentTimeMillis());
                    count.addAndGet(result.size());
                    if (count.get() >= 1_00_000) {
                        var now = System.currentTimeMillis();
                        log.info("Process {} messages in {} ms", count.get(), now - start.get());
                        start.set(now);
                        count.set(0);
                    }
                });
        });
    }

    public void cleanMessageBatch(List<Message> batch) {
        var entries = batch.stream()
            .peek(message -> log.debug("Delete message: {}", message.messageId()))
            .map(message -> DeleteMessageBatchRequestEntry.builder()
                .id(message.messageId())
                .receiptHandle(message.receiptHandle())
                .build()).toList();
        sqsClient.deleteMessageBatch(DeleteMessageBatchRequest.builder()
            .queueUrl(QueueConfig.QUEUE_URL)
            .entries(entries)
            .build());
    }

    public Flux<Message> persistMessageBatch(List<Message> messages) {
        return Flux.fromIterable(messages)
            .map(message -> {
                MessageAttributeValue value = message.messageAttributes().get("grpc");
                TransferRequest request = null;
                try {
                    request = TransferRequest.parseFrom(value.binaryValue().asByteArray());
                    log.debug("Received message: {} {}", message, request);
                    transactionService.persistTransaction(request).subscribe();
                } catch (Exception e) {
                    // TODO: handle parse fail request
                    log.error("Failed to parse message: {}", message);
                }
                return message;
            });
    }

    @PreDestroy
    public void destroy() throws InterruptedException {
        executorService.shutdown();
        executorService.awaitTermination(20, SECONDS);
    }

}
