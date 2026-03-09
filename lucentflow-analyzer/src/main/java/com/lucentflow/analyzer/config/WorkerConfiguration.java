package com.lucentflow.analyzer.config;

import com.lucentflow.analyzer.worker.RugCheckWorker;
import com.lucentflow.analyzer.worker.WhaleAnalysisWorker;
import com.lucentflow.common.pipeline.TransactionPipe;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

/**
 * Spring configuration for worker registration and dependency injection resolution.
 * 
 * <p>Implementation Details:
 * Resolves circular dependencies between TransactionPipe and analysis workers through
 * PostConstruct registration pattern. Provides plug-and-play extensibility for the
 * whale analysis pipeline. Virtual thread compatible through stateless configuration
 * and immutable worker references. Ensures proper initialization order in Spring context.
 * </p>
 * 
 * @author ArchLucent
 * @since 1.0
 */
@Slf4j
@Configuration
public class WorkerConfiguration {
    
    private final TransactionPipe transactionPipe;
    private final WhaleAnalysisWorker whaleAnalysisWorker;
    private final RugCheckWorker rugCheckWorker;
    
    @Autowired
    public WorkerConfiguration(TransactionPipe transactionPipe, 
                             WhaleAnalysisWorker whaleAnalysisWorker,
                             RugCheckWorker rugCheckWorker) {
        this.transactionPipe = transactionPipe;
        this.whaleAnalysisWorker = whaleAnalysisWorker;
        this.rugCheckWorker = rugCheckWorker;
    }
    
    /**
     * Registers all analysis workers with the TransactionPipe after Spring context initialization.
     * 
     * <p>Executes during PostConstruct phase to ensure all beans are properly instantiated.
     * Demonstrates plug-and-play architecture by dynamically registering whale and rug check workers.
     * Provides extensibility for future worker implementations without code changes.</p>
     */
    @PostConstruct
    public void registerWorkers() {
        log.info("Registering workers with TransactionPipe");
        
        // Register whale analysis worker
        transactionPipe.registerConsumer(whaleAnalysisWorker);
        log.info("✅ Registered WhaleAnalysisWorker");
        
        // Register rug check worker
        transactionPipe.registerConsumer(rugCheckWorker);
        log.info("✅ Registered RugCheckWorker");
        
        log.info("🚀 TransactionPipe initialized with {} workers", 2);
        log.info("🔌 Plug-and-play extensibility demonstrated successfully!");
    }
}
