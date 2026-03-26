package com.lucentflow.indexer.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Configuration for task executors with graceful shutdown support.
 */
@Slf4j
@Configuration
public class ExecutorConfig {
    
    /**
     * Indexer module task executor with graceful shutdown configuration.
     */
    @Bean(name = "indexerTaskExecutor")
    public Executor indexerTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(8);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(100);
        executor.setThreadFactory(Thread.ofVirtual().name("indexer-task-", 0).factory());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(5);
        executor.setRejectedExecutionHandler((r, executor1) -> {
            log.warn("Task rejected during shutdown: {}", r.toString());
        });
        executor.initialize();
        return executor;
    }
}
