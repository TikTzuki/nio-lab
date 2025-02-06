package org.nio.client.grpc

import com.google.protobuf.Empty
import com.nio.wallet.grpc.ReactorWalletServiceGrpc.ReactorWalletServiceStub
import com.nio.wallet.grpc.WalletServiceOuterClass.CreateAccountRequest
import com.nio.wallet.grpc.WalletServiceOuterClass.CreateAccountResponse
import net.devh.boot.grpc.client.inject.GrpcClient
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.core.io.buffer.DataBufferUtils
import org.springframework.core.io.buffer.DefaultDataBufferFactory
import org.springframework.http.ContentDisposition
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.codec.multipart.FilePart
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@RestController
@RequestMapping("/api/v1/wallet")
class TestController @Autowired constructor(
    @GrpcClient("bank-account-service")
    val stub: ReactorWalletServiceStub,
    val workerService: GrpcWorkerService
) {
    @PostMapping("/ping")
    fun test(): Flux<String> {
        val f = Flux.generate({ 0 }, { state, sink ->
            if (state < 10) {
                sink.next(Empty.getDefaultInstance())
                state + 1
            } else {
                sink.complete()
                state
            }
        })
        return stub.ping(f).map { it.value }
    }

    @PostMapping("/bulk-insert-accounts")
    fun bulkInsertAccounts(@RequestParam number: Int): ResponseEntity<Flux<DataBuffer>> {
        val reqFlux = Flux
            .just(
                CreateAccountRequest.newBuilder()
                    .setBalance("100")
                    .build()
            )
            .repeat(number.toLong() - 1)
            .flatMap {
                val resp: Mono<CreateAccountResponse> = stub.createAccount(Mono.just(it))
                return@flatMap resp
            }

        val bufferFlux: Flux<DataBuffer> = reqFlux.map {
            val bytes = (it.accountId + "\n").toByteArray(Charsets.UTF_8)
            return@map DefaultDataBufferFactory.sharedInstance.wrap(bytes)
        }
        return ResponseEntity.ok()
            .headers {
                it.contentType = MediaType.parseMediaType("text/csv")
                it.contentDisposition = ContentDisposition.parse("attachment; filename=\"data.csv\"")
            }
            .body(bufferFlux)
    }

    @PostMapping("/bulk-transfers", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun bulkTransfers(
        @RequestParam(required = false) userCount: Long?,
        @RequestParam(defaultValue = "1") transactionPerUser: Int,
        @RequestParam(defaultValue = "true") enableStream: Boolean,
        @RequestPart("file")
        fileFlux: FilePart
    ): String {
        val accountIds: List<String> = DataBufferUtils.join(fileFlux.content())
            .map { buffer ->
                buffer.asInputStream(true)
                    .bufferedReader().use { reader ->
                        reader.readLines().stream().limit(userCount ?: Long.MAX_VALUE).toList()
                    }
            }.block()!!
        workerService.startRun(accountIds, transactionPerUser, enableStream)
        return "Bulk Transfer!"
    }

    @GetMapping("/stop-transfers")
    fun stopTransfers(): String {
        workerService.stopRun()
        return "Stopped"
    }

    companion object
}
