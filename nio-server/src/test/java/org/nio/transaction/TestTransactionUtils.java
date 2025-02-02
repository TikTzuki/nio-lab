package org.nio.transaction;

import com.nio.wallet.grpc.WalletServiceOuterClass;
import org.apache.commons.lang3.RandomStringUtils;
import org.nio.sqs.QueuePublisherKt;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;

import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TestTransactionUtils {
    public static Queue<Message> generateMessages(List<String> users, int requestPerUser) {
        AtomicReference<Long> counter = new AtomicReference<>(0L);
        return users.stream()
            .flatMap(accountId -> {
                return Stream.generate(() -> {
                        String refId = String.valueOf(counter.getAndSet(counter.get() + 1));
                        var traceId = "trace-" + refId;
                        var spanId = "span-" + refId;
                        return WalletServiceOuterClass.TransferRequest.newBuilder()
                            .setTraceId(traceId)
                            .setSpanId(spanId)
                            .setReferenceId(refId)
                            .setUserId(accountId)
                            .setAmount(RandomStringUtils.insecure().nextNumeric(2))
                            .build();
                    })
                    .limit(requestPerUser);
            })
            .map(transaction -> {
                var transactionContent = MessageAttributeValue.builder()
                    .binaryValue(SdkBytes.fromByteArray(transaction.toByteArray()))
                    .dataType("Binary")
                    .build();
                return Message.builder()
                    .messageAttributes(Map.of(
                        QueuePublisherKt.HASH_RING_INDEX, MessageAttributeValue.builder()
                            .stringValue(String.valueOf(transaction.getUserId().hashCode()))
                            .dataType("String").build(),
                        QueuePublisherKt.MESSAGE_CONTENT, transactionContent,
                        QueuePublisherKt.TRACE_ID, MessageAttributeValue.builder().stringValue("traceId").dataType("String").build(),
                        QueuePublisherKt.SPAN_ID, MessageAttributeValue.builder().stringValue("spanId").dataType("String").build()
                    ))
                    .messageId(transaction.getReferenceId())
                    .body(transaction.getReferenceId())
                    .build();
            }).collect(Collectors.toCollection(LinkedBlockingQueue::new));
    }
}
