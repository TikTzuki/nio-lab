package org.nio.transaction

import com.nio.wallet.grpc.WalletServiceOuterClass.TransferRequest
import com.nio.wallet.grpc.WalletServiceOuterClass.TransferResponse
import reactor.core.publisher.Flux
import reactor.kotlin.core.publisher.toFlux
import software.amazon.awssdk.services.sqs.model.SendMessageBatchResponse

fun TransferRequest.genericFail(): TransferResponse {
    return TransferResponse.newBuilder()
        .setCode(-1)
        .setTraceId(traceId)
        .setSpanId(spanId)
        .setReferenceId(referenceId)
        .build()
}

fun TransferRequest.genericSuccess(): TransferResponse {
    return TransferResponse.newBuilder()
        .setCode(0)
        .setTraceId(traceId)
        .setSpanId(spanId)
        .setReferenceId(referenceId)
        .build()
}


fun mapBatchResponse(batch: List<TransferRequest>, response: SendMessageBatchResponse): Flux<TransferResponse> {
    val batchMap = batch.associateBy { it.referenceId }
    return Flux.concat(
        response.failed().toFlux().mapNotNull { batchMap[it.id()]?.genericFail() },
        response.successful().toFlux().mapNotNull { batchMap[it.id()]?.genericSuccess() }
    )
}