package org.nio.endpoint;

import com.google.protobuf.Empty;
import com.google.protobuf.StringValue;
import com.nio.wallet.grpc.ReactorWalletServiceGrpc;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import org.nio.account.AccountService;
import org.nio.transaction.impl.TransactionServiceImpl;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;

import static com.nio.wallet.grpc.WalletServiceOuterClass.*;

@Slf4j
@GrpcService
@RequiredArgsConstructor
public class WalletGrpcEndpoint extends ReactorWalletServiceGrpc.WalletServiceImplBase {
    private static final Long TIMEOUT_MILLIS = 5_000L;
    final AccountService bankAccountService;
    final TransactionServiceImpl transactionService;

    @Override
    public Flux<StringValue> ping(Flux<Empty> request) {
        return request.map(req -> StringValue.newBuilder()
                .setValue("pong")
                .build())
            .timeout(Duration.ofMillis(TIMEOUT_MILLIS));
    }

    @Override
    public Mono<CreateAccountResponse> createAccount(Mono<CreateAccountRequest> request) {
        return request.flatMap(req -> bankAccountService.createAccount(MapperKt.of(req))
            .map(bankAccount -> CreateAccountResponse.newBuilder()
                .setAccountId(bankAccount)
                .build())
            .timeout(Duration.ofMillis(TIMEOUT_MILLIS))
            .doOnError(ex -> {
                log.error(ex.getMessage(), ex);
            })
            .doOnSuccess(result -> log.debug("created account: {}", result.getAccountId())));
    }

    @Override
    public Flux<TransferResponse> transferStream(Flux<TransferRequest> request) {
        return transactionService.prepareTransfer(request.map(MapperKt::newInstanceWithSpanId))
            .timeout(Duration.ofMillis(TIMEOUT_MILLIS));
    }

    @Override
    public Mono<TransferResponse> transfer(Mono<TransferRequest> request) {
        return transactionService.prepareTransfer(request.flux().map(MapperKt::newInstanceWithSpanId))
            .next()
            .thenReturn(TransferResponse.getDefaultInstance())
            .timeout(Duration.ofMillis(TIMEOUT_MILLIS))
            .doOnError(ex -> {
                log.error(ex.getMessage(), ex);
            })
            .doOnSuccess(result -> log.debug("Write success tran: {}", result));
    }

    @Override
    public Mono<CloseTicketResponse> closeTicket(Mono<CloseTicketRequest> request) {
        return Mono.empty();
    }

    @Override
    public Mono<GetAccountByIdResponse> getAccountById(Mono<GetAccountByIdRequest> request) {
        return Mono.just(GetAccountByIdResponse.newBuilder()
            .build());
    }

    @Override
    public Mono<WithdrawResponse> withdraw(Mono<WithdrawRequest> request) {
        return request.map(req -> {
            return req;
        }).map(req -> WithdrawResponse.newBuilder()
            .build());
    }

}
