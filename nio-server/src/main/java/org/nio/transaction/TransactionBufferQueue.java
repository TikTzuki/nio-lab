package org.nio.transaction;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nio.config.QueueConfig;
import org.nio.config.TransactionConfig;
import org.nio.sqs.QueuePublisherKt;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.GroupedFlux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SynchronousSink;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;

@Slf4j
@RequiredArgsConstructor
@Component
public class TransactionBufferQueue {
    final SqsAsyncClient sqsClient;
    final TransactionConfig transactionConfig;

    public Flux<GroupedFlux<Long, Message>> pollMessageFlux() {
        ReceiveMessageRequest receiveMessageRequest = ReceiveMessageRequest.builder()
            .queueUrl(QueueConfig.QUEUE_URL)
            .messageAttributeNames(QueuePublisherKt.MESSAGE_CONTENT, QueuePublisherKt.TRACE_ID, QueuePublisherKt.SPAN_ID, QueuePublisherKt.HASH_RING_INDEX)
            .waitTimeSeconds((int) transactionConfig.getReceiveMessageWaitTime().toSeconds())
            .maxNumberOfMessages(transactionConfig.getNumberOfMessages())
            .build();

        Callable<Long> initState = () -> 0L;
        BiFunction<Long, SynchronousSink<Long>, Long> infinityMessageGenerator = (i, sink) -> {
            sink.next(i);
            return i;
        };
        AtomicLong count = new AtomicLong();
        AtomicLong start = new AtomicLong(System.currentTimeMillis());
        return Flux.generate(initState, infinityMessageGenerator)
//            .delayElements(Duration.ofSeconds(30))
            .concatMap(i -> Mono.fromFuture(sqsClient.receiveMessage(receiveMessageRequest)))
            .flatMap(sqsMessages -> {
                if (sqsMessages.hasMessages()) {
                    var messages = sqsMessages.messages();

                    // debug
                    count.getAndAdd(messages.size());
                    if (count.get() >= 1_000) {
                        var now = System.currentTimeMillis();
                        log.info("Receive then delete {} messages in {} ms", count.get(), now - start.get());
                        start.set(now);
                        count.set(0);
                    }
                    return Flux.fromIterable(messages);
                }
                return Flux.empty();
            })
            .groupBy(message ->
                Long.valueOf(message.messageAttributes()
                    .get(QueuePublisherKt.HASH_RING_INDEX)
                    .stringValue()));
    }


}
