package org.nio.sqs

import com.nio.wallet.grpc.WalletServiceOuterClass.TransferRequest
import mu.KotlinLogging
import org.nio.config.QueueConfig
import org.nio.config.TransactionConfig
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import software.amazon.awssdk.core.SdkBytes
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.*
import kotlin.math.abs

val logger = KotlinLogging.logger {}

const val MESSAGE_CONTENT = "messageContent"
const val TRACE_ID = "traceId"
const val SPAN_ID = "spanId"
const val HASH_RING_INDEX = "hashRingIndex"

interface QueuePublisher {
    fun publish(transactions: List<TransferRequest>): Mono<SendMessageBatchResponse>

    companion object {
        @JvmStatic
        fun hashRingIndex(message: Message): Int? {
            return message.messageAttributes()[HASH_RING_INDEX]?.stringValue()?.toInt()
        }
    }
}

@Component
class QueuePublisherMockImpl : QueuePublisher {
    override fun publish(transactions: List<TransferRequest>): Mono<SendMessageBatchResponse> {
        val startBatch = System.currentTimeMillis()
        return Mono.just(
            SendMessageBatchResponse.builder()
                .successful(
                    transactions.map {
                        SendMessageBatchResultEntry.builder()
                            .id(it.referenceId)
                            .build()
                    })
                .build()
        )
            .doOnNext {
                logger.debug(
                    "Published batch: {} - take {} ms",
                    transactions.size,
                    System.currentTimeMillis() - startBatch,
                )
            }
    }
}

//@Component
class QueuePublisherImpl @Autowired constructor(
    private val client: SqsAsyncClient,
    private val transactionConfig: TransactionConfig
) : QueuePublisher {

    fun getRingIndex(userId: String): Int {
        return abs(userId.hashCode()) % transactionConfig.hashRingSize
    }

    override fun publish(transactions: List<TransferRequest>): Mono<SendMessageBatchResponse> {
        val startBatch = System.currentTimeMillis()
        val entries = transactions.map { transaction ->
            val transactionContent = MessageAttributeValue.builder()
                .binaryValue(SdkBytes.fromByteArray(transaction.toByteArray()))
                .dataType("Binary")
                .build()
            return@map SendMessageBatchRequestEntry.builder()
                .messageAttributes(
                    mapOf(
                        HASH_RING_INDEX to MessageAttributeValue.builder()
                            .stringValue(getRingIndex(transaction.userId).toString())
                            .dataType("String")
                            .build(),
                        MESSAGE_CONTENT to transactionContent,
                        TRACE_ID to MessageAttributeValue.builder()
                            .stringValue(transaction.traceId)
                            .dataType("String")
                            .build(),
                        SPAN_ID to MessageAttributeValue.builder()
                            .stringValue(transaction.spanId)
                            .dataType("String")
                            .build()
                    )
                )
                .id(transaction.referenceId)
                .messageBody(transaction.referenceId)
                .build()
        }
        logger.debug(
            "Prepare batch: {} - {}",
            System.currentTimeMillis() - startBatch,
            entries.size,
        )
        val batchResponse = client.sendMessageBatch(
            SendMessageBatchRequest.builder()
                .queueUrl(QueueConfig.QUEUE_URL)
                .entries(entries)
                .build()
        )
        logger.debug(
            "Published batch: {} - {}",
            System.currentTimeMillis() - startBatch,
            entries.size
        )
        return Mono.fromFuture(batchResponse)
    }
}