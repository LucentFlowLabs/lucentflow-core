package com.lucentflow.analyzer.pipeline;

import com.lucentflow.common.event.WhaleDetectedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Event-driven orchestrator for whale transaction analysis pipeline coordination.
 * 
 * <p>Implementation Details:
 * Asynchronously processes WhaleDetectedEvent through registered analysis handlers.
 * Provides fault-tolerant execution with handler isolation to prevent cascade failures.
 * Virtual thread compatible through @Async annotation and non-blocking event handling.
 * Ensures comprehensive analysis coverage while maintaining system resilience.
 * </p>
 * 
 * @author ArchLucent
 * @since 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnalysisOrchestrator {
    
    private final List<WhaleAnalysisHandler> analysisHandlers;
    
    /**
     * Asynchronously processes whale detection events through all registered analysis handlers.
     * 
     * <p>Executes each handler independently with fault isolation to ensure
     * comprehensive analysis coverage. Failed handlers do not prevent execution
     * of remaining handlers, maintaining system resilience and analysis completeness.</p>
     * 
     * @param event Whale detection event containing transaction data and block context
     * @throws IllegalArgumentException if event is null
     */
    @EventListener
    @Async
    public void handleWhaleDetected(WhaleDetectedEvent event) {
        log.info("Processing whale detection: {} ETH | {} → {} | Block: {}",
                event.whaleTransaction().getValueEth(),
                event.whaleTransaction().getFromAddress(),
                event.whaleTransaction().getToAddress(),
                event.blockNumber());
        
        // Execute all analysis handlers
        for (WhaleAnalysisHandler handler : analysisHandlers) {
            try {
                handler.handle(event.whaleTransaction());
            } catch (Exception e) {
                log.error("Analysis handler {} failed for transaction {}", 
                        handler.getClass().getSimpleName(), 
                        event.whaleTransaction().getHash(), e);
                // Continue with other handlers
            }
        }
        
        log.debug("Completed analysis for whale transaction: {}", 
                event.whaleTransaction().getHash());
    }
}
