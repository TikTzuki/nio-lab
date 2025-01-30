package org.nio.transaction.impl;

import com.datastax.oss.driver.api.core.cql.BatchStatement;
import com.datastax.oss.driver.api.core.cql.BatchType;
import com.datastax.oss.driver.api.core.cql.SimpleStatement;
import lombok.RequiredArgsConstructor;
import org.nio.transaction.NewTransaction;
import org.nio.transaction.Transaction;
import org.nio.transaction.TransactionByAccount;
import org.nio.transaction.TransactionCustomRepository;
import org.springframework.data.cassandra.core.ReactiveCassandraTemplate;
import org.springframework.data.cassandra.core.cql.WriteOptions;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class TransactionRepositoryImpl implements TransactionCustomRepository {
    final ReactiveCassandraTemplate template;

    @Override
    public Mono<NewTransaction> insertBatch(Transaction transaction) {
        SimpleStatement transactionStatement = template.getStatementFactory()
            .insert(transaction, WriteOptions.builder()
                .build()).build();
        SimpleStatement transactionByUserStatement = template.getStatementFactory()
            .insert(new TransactionByAccount(transaction.getAccountId(), transaction.getId(), transaction.getTimeStamp()), WriteOptions.builder()
                .build()).build();

        BatchStatement statements = BatchStatement.newInstance(BatchType.UNLOGGED, transactionStatement, transactionByUserStatement); // Keyspace not support LoggedBatch
        return template.execute(statements)
            .map(r -> new NewTransaction(transaction.getId(), transaction.getRefId()));
    }
}
