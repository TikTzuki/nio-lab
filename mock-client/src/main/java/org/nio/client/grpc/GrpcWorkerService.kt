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
import reactor.core.Disposable
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
    var runningFlux: Disposable? = null
    var flux: Flux<String>? = null

    fun startRun(
        accountIds: List<String>,
        transPerUser: Int,
        enableStream: Boolean
    ) {
        if (!executorService.isShutdown)
            stopRun()
        flux = accountIds.toFlux()
        executorService = Executors.newSingleThreadScheduledExecutor()
        executorService.scheduleAtFixedRate({
            transferStream(flux!!, accountIds.size, Runtime.getRuntime().availableProcessors(), transPerUser)
        }, 0, 5, TimeUnit.SECONDS)
    }

    fun transferStream(
        accountIds: Flux<String>,
        size: Int,
        parallel: Int,
        transPerUser: Int
    ): Disposable {
        val start = System.currentTimeMillis()
        var successCount = 0
        var failCount = 0
        return accountIds
            .bufferTimeout(ceil(size / parallel.toDouble()).toInt(), Duration.ofMillis(1))
            .parallel(parallel)
            .runOn(Schedulers.parallel())
            .flatMap { userAccountIds ->
                logger.info { "Transfer ${userAccountIds.size} transactions " }
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
//            .sequential()
            .doOnComplete { logger.info { "Transfer $successCount transaction, success ${successCount}, fail ${failCount}, taken: ${System.currentTimeMillis() - start} ms" } }
            .subscribe { }
    }

    fun accountIdsToTransferRequest(accountIds: List<String>, transPerUser: Int): Flux<TransferRequest> {
        return accountIds.toFlux()
            .flatMap { accountId ->
                Mono.just(accountIds)
                    .repeat(transPerUser.toLong() - 1)
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
        runningFlux?.dispose()
        executorService.shutdown()
        executorService.awaitTermination(1, TimeUnit.MINUTES)
    }

    companion object : KLogging()
}