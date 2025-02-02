package org.nio.transaction;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.nio.config.TransactionConfig;
import org.nio.sqs.QueuePublisherKt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

import java.time.Duration;
import java.time.LocalTime;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.stream.Stream;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
public class TransactionBufferQueueTest {
    private static final Logger log = LoggerFactory.getLogger(TransactionBufferQueueTest.class);
    @Mock
    SqsAsyncClient sqsClient;
    @Mock
    TransactionConfig config;

    TransactionBufferQueue queue;

    @BeforeEach
    void setup() {
        queue = new TransactionBufferQueue(sqsClient, config);
    }


    @Test
    public void testQueue() throws InterruptedException {
        // given
        given(config.getReceiveMessageWaitTime()).willReturn(Duration.ofSeconds(20));
        given(config.getNumberOfMessages()).willReturn(10);
        var allMessages = TestTransactionUtils.generateMessages(List.of("u1", "u2", "u3", "u4"), 5);
        given(sqsClient.receiveMessage(any(ReceiveMessageRequest.class)))
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
        // when & then
        var f = queue.pollMessageFlux()
            .map(message -> {
                try {
                    var logContent = "%s [%s]: %s".formatted(LocalTime.now(), Thread.currentThread().getName(), message.body());
                    Thread.sleep(new Random().nextInt(0, 300));
                    System.out.println(logContent);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                return message;
            })
            .subscribe(message -> {
                var hash = message.messageAttributes().get(QueuePublisherKt.HASH_RING_INDEX).stringValue();
                var logContent = "%s [%s]: %s - %s".formatted(LocalTime.now(), Thread.currentThread().getName(), message.body(), hash);
                System.out.println(logContent);
            });
        Thread.sleep(300 * 20);
//        StepVerifier.create(queue.queue())
//            .expectNextCount(20)
//            .consumeNextWith(m -> {
//                System.out.println(m);
//                log.info("===> {}", m);
//            })
//            .verifyTimeout(Duration.ofSeconds(2));
        System.out.println("Done");
    }
}
