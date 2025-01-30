package org.nio.sqs

import com.nio.wallet.grpc.WalletServiceOuterClass.TransferRequest
import mu.KotlinLogging
import org.nio.config.QueueConfig
import software.amazon.awssdk.core.SdkBytes
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequest
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequestEntry
import software.amazon.awssdk.services.sqs.model.SendMessageBatchResponse

val logger = KotlinLogging.logger {}

const val MESSAGE_CONTENT = "messageContent"
const val TRACE_ID = "traceId"
const val SPAN_ID = "spanId"

fun SqsClient.publish(transactions: List<TransferRequest>): SendMessageBatchResponse {
    val startBatch = System.currentTimeMillis()
    val entries = transactions.map { transaction ->
        val transactionContent = MessageAttributeValue.builder()
            .binaryValue(SdkBytes.fromByteArray(transaction.toByteArray()))
            .dataType("Binary")
            .build()
        return@map SendMessageBatchRequestEntry.builder()
            .messageAttributes(
                mapOf(
                    MESSAGE_CONTENT to transactionContent,
                    TRACE_ID to MessageAttributeValue.builder().stringValue(transaction.traceId).dataType("String").build(),
                    SPAN_ID to MessageAttributeValue.builder().stringValue(transaction.spanId).dataType("String").build()
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
    val batchResponse: SendMessageBatchResponse = this.sendMessageBatch(
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
    return batchResponse
}