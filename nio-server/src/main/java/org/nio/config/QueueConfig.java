package org.nio.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

import java.net.URISyntaxException;
import java.util.Objects;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class QueueConfig implements InitializingBean {
    public static String QUEUE_URL = "";
    final QueueConfigProperties properties;

    @Override
    public void afterPropertiesSet() {
        QUEUE_URL = properties.getUrl();
    }

    @Bean
    public SqsAsyncClient sqsClient() throws URISyntaxException {
        var builder = SqsAsyncClient.builder()
            .region(properties.getRegion())
            .credentialsProvider(ProfileCredentialsProvider.create());
        if (Objects.nonNull(properties.getUrlEndpoint())) {
            builder.endpointOverride(properties.getUrlEndpoint().toURI());
        }
        return builder.build();
    }

}