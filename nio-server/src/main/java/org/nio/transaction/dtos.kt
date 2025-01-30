package org.nio.transaction

import com.nio.wallet.grpc.WalletServiceOuterClass.TransferRequest

data class NewTransaction(
    val id: String,
    val refId: String
)

data class FailedTransaction(
    val traceId: String,
    val spanId: String,
    val referenceId: String,
    val error: String?,
) {
    companion object {
        @JvmStatic
        fun fromTransferRequest(newTransaction: TransferRequest, ex: Throwable?): FailedTransaction {
            return FailedTransaction(
                traceId = newTransaction.traceId,
                spanId = newTransaction.spanId,
                referenceId = newTransaction.referenceId,
                error = ex?.toString()
            )
        }
    }
}