package com.drivewealth.Reconciliation_Engine.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RequiredArgsConstructor
@Slf4j
@Configuration
public class ThreadPoolConfig {

    private final ReconciliationProperties reconciliationProperties;

    @Bean(name = "reconciliationExecutor")
    public ExecutorService reconciliationExecutor() {
        int cores = Runtime.getRuntime().availableProcessors();
        int dynamicSize = cores * 10;

        int threadPoolSize = reconciliationProperties.getThreadPoolSize() > 0
                ? reconciliationProperties.getThreadPoolSize()
                : dynamicSize;

        log.info("Available CPU Cores : {}", cores);
        log.info("Using thread pool size : {}", threadPoolSize);

        return Executors.newFixedThreadPool(threadPoolSize);
    }

}
