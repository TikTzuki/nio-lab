package org.nio

import org.nio.config.QueueConfigProperties
import org.nio.config.TransactionConfig
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication

@SpringBootApplication
@EnableConfigurationProperties(QueueConfigProperties::class, TransactionConfig::class)
class Main

fun main(args: Array<String>) {
    runApplication<Main>(*args)
}
