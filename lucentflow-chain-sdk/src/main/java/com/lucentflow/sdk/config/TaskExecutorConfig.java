package com.lucentflow.sdk.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Spring Boot configuration for high-performance asynchronous task execution.
 * 
 * <p>Implementation Details:
 * Configures bounded ThreadPoolTaskExecutor with custom thread factory for
 * virtual thread-like performance on Java 21. Implements backpressure through
 * bounded queue capacity and CallerRunsPolicy rejection handling.
 * Optimized for blockchain processing workloads with controlled concurrency.
 * </p>
 * 
 * @author ArchLucent
 * @since 1.0
 */
@Configuration
@EnableAsync
public class TaskExecutorConfig {
    
    /**
     * Creates and configures a bounded ThreadPoolTaskExecutor for async processing.
     * 
     * <p>Executor configuration optimized for blockchain workloads:
     * - Custom thread factory with daemon threads and naming convention
     * - Bounded queue (1000 capacity) for memory management and backpressure
     * - Core pool size of 10 with scaling to 50 maximum threads
     * - CallerRunsPolicy rejection handling for graceful degradation
     * - 60-second thread keep-alive for resource efficiency</p>
     * 
     * @return Configured TaskExecutor bean named "globalLucentTaskExecutor"
     */
    @Bean(name = "globalLucentTaskExecutor")
    public TaskExecutor globalLucentTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        // Use custom thread factory for virtual-like performance on Java 17
        executor.setThreadFactory(runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName("global-task-" + thread.getId());
            thread.setDaemon(true);
            return thread;
        });
        
        // Configure bounded queue for backpressure
        executor.setQueueCapacity(1000);
        
        // Set reasonable pool limits
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(50);
        executor.setKeepAliveSeconds(60);
        
        // Configure rejection policy for backpressure
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        
        // Initialize executor
        executor.initialize();
        
        return executor;
    }
}
