package com.lucentflow.common.lease;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.SmartLifecycle;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * TTL lease heartbeat that elects a single indexer/analyzer writer.
 * When {@code lucentflow.lease.enabled=false}, this process is always treated as leader (local/dev).
 *
 * @author ArchLucent
 * @since 1.2
 */
@Slf4j
@Component
@ConditionalOnExpression("'${lucentflow.runtime.enable-indexer:true}' == 'true' or '${lucentflow.runtime.enable-analyzer:true}' == 'true'")
public class WorkerLeaseCoordinator implements LeadershipGate, SmartLifecycle {

    private final WorkerLeaseService workerLeaseService;
    private final AtomicBoolean leader = new AtomicBoolean(false);
    private final AtomicBoolean running = new AtomicBoolean(false);

    private final String holderId;
    private final String leaseName;
    private final boolean leaseEnabled;
    private final Duration ttl;

    public WorkerLeaseCoordinator(
            WorkerLeaseService workerLeaseService,
            @Value("${lucentflow.lease.enabled:true}") boolean leaseEnabled,
            @Value("${lucentflow.lease.name:indexer-leader}") String leaseName,
            @Value("${lucentflow.lease.ttl-seconds:15}") long ttlSeconds
    ) {
        this.workerLeaseService = workerLeaseService;
        this.leaseEnabled = leaseEnabled;
        this.leaseName = leaseName == null || leaseName.isBlank() ? "indexer-leader" : leaseName.trim();
        this.ttl = Duration.ofSeconds(Math.max(1L, ttlSeconds));
        this.holderId = resolveHolderId();
    }

    private static String resolveHolderId() {
        String hostname = System.getenv("HOSTNAME");
        if (hostname != null && !hostname.isBlank()) {
            return hostname.trim();
        }
        String computerName = System.getenv("COMPUTERNAME");
        if (computerName != null && !computerName.isBlank()) {
            return computerName.trim() + "-" + UUID.randomUUID().toString().substring(0, 8);
        }
        return "worker-" + UUID.randomUUID();
    }

    @Override
    public boolean isLeader() {
        if (!leaseEnabled) {
            return true;
        }
        return leader.get();
    }

    @Scheduled(fixedDelayString = "${lucentflow.lease.renew-interval-seconds:5}000")
    public void renewLease() {
        if (!running.get() || !leaseEnabled) {
            return;
        }
        boolean held = workerLeaseService.tryAcquireOrRenew(leaseName, holderId, ttl);
        boolean wasLeader = leader.getAndSet(held);
        if (held && !wasLeader) {
            log.info("Acquired worker lease '{}' as holder={}", leaseName, holderId);
        } else if (!held && wasLeader) {
            log.warn("Lost worker lease '{}' (holder={})", leaseName, holderId);
        }
    }

    @Override
    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        if (!leaseEnabled) {
            leader.set(true);
            log.info("Worker lease disabled; treating process as permanent leader (holder={})", holderId);
            return;
        }
        renewLease();
        log.info("WorkerLeaseCoordinator started (lease={}, holder={}, ttl={}s)",
                leaseName, holderId, ttl.toSeconds());
    }

    @Override
    public void stop() {
        doStop();
    }

    @Override
    public void stop(Runnable callback) {
        try {
            doStop();
        } finally {
            callback.run();
        }
    }

    private void doStop() {
        if (!running.getAndSet(false)) {
            return;
        }
        if (leaseEnabled) {
            try {
                workerLeaseService.release(leaseName, holderId);
            } catch (Exception ex) {
                log.warn("Failed to release worker lease '{}': {}", leaseName, ex.toString());
            }
        }
        leader.set(false);
        log.info("WorkerLeaseCoordinator stopped (holder={})", holderId);
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }

    @Override
    public int getPhase() {
        // Start before indexer producer / analyzer consumers.
        return Integer.MAX_VALUE - 200;
    }

    String holderIdForTests() {
        return holderId;
    }
}
