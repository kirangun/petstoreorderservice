package com.chtrembl.petstore.order.config;

import com.azure.cosmos.ConsistencyLevel;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.spring.data.cosmos.config.AbstractCosmosConfiguration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CosmosConfiguration extends AbstractCosmosConfiguration {

    @Value("${petstore.cosmos.url:}")
    private String url;

    @Value("${petstore.cosmos.key:}")
    private String key;

    @Bean
    public CosmosClientBuilder configureCosmosClientBuilder() {
        return new CosmosClientBuilder()
                .endpoint(url)
                .key(key)
                .consistencyLevel(ConsistencyLevel.SESSION);
    }

    @Override
    protected String getDatabaseName() {
        return "petstore";
    }
}