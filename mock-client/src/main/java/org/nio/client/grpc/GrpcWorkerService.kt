package org.nio.client.grpc

import com.nio.wallet.grpc.ReactorWalletServiceGrpc.ReactorWalletServiceStub
import com.nio.wallet.grpc.WalletServiceOuterClass.ErrorDetail
import com.nio.wallet.grpc.WalletServiceOuterClass.TransferRequest
import io.grpc.StatusRuntimeException
import io.grpc.protobuf.ProtoUtils
import mu.KLogging
import net.devh.boot.grpc.client.inject.GrpcClient
import org.nio.client.utils.genReferenceId
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import reactor.kotlin.core.publisher.toFlux
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlin.math.ceil


@Service
class GrpcWorkerService @Autowired constructor(
    @GrpcClient("bank-account-service")
    val stub: ReactorWalletServiceStub
) {
    var executorService: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()

    fun startRun(
        accountIds: List<String>,
        parallel: Int,
        transPerUser: Int,
        enableStream: Boolean
    ) {
        if (!executorService.isShutdown)
            stopRun()
        executorService = Executors.newSingleThreadScheduledExecutor()
        executorService.scheduleAtFixedRate({
            transferStream(accountIds, parallel, transPerUser)
        }, 0, 1, TimeUnit.SECONDS)
    }

    fun transferStream(
        accountIds: List<String>,
        parallel: Int,
        transPerUser: Int
    ) {
        val start = System.currentTimeMillis()
        var successCount = 0
        var failCount = 0
        accountIds.toFlux()
            .bufferTimeout(ceil(accountIds.size / parallel.toDouble()).toInt(), Duration.ofMillis(1))
            .parallel(parallel)
            .runOn(Schedulers.parallel())
            .flatMap { userAccountIds ->
                stub.transferStream(accountIdsToTransferRequest(userAccountIds, transPerUser))
                    .doOnNext { response ->
                        if (response.code == 0)
                            successCount++
                        else
                            failCount++
                    }
                    .onErrorContinue { err, _ ->
                        if (err is StatusRuntimeException) {
                            val error = err.trailers?.get(ProtoUtils.keyForProto(ErrorDetail.getDefaultInstance()))
                            logger.error("Map error response: ${error?.code} ${error?.message} $error")
                        } else
                            logger.error { "Unhandled error $err" }
                    }
            }
            .doOnComplete { logger.info { "Transfer $successCount transaction, success ${successCount}, fail ${failCount}, taken: ${System.currentTimeMillis() - start} ms" } }
            .subscribe { }
    }

    fun accountIdsToTransferRequest(accountIds: List<String>, transPerUser: Int): Flux<TransferRequest> {
        return accountIds.toFlux()
            .flatMap { accountId ->
                Mono.just(accountIds)
                    .repeat(transPerUser.toLong())
                    .map {
                        val refId = genReferenceId(accountId)
                        val traceId = "trace-$refId"
                        val spanId = "span-$refId"
                        TransferRequest.newBuilder()
                            .setTraceId(traceId)
                            .setSpanId(spanId)
                            .setReferenceId(refId)
                            .setUserId(accountId)
                            .setAmount("1")
                            .build()
                    }
            }
    }

    fun stopRun() {
        executorService.shutdown()
        executorService.awaitTermination(1, TimeUnit.MINUTES)
    }

    companion object : KLogging()
}