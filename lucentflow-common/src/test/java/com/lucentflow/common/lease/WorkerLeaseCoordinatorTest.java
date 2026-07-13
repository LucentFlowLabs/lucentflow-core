package com.lucentflow.common.lease;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for lease coordinator promote/demote and disabled mode.
 *
 * @author ArchLucent
 * @since 1.2
 */
@ExtendWith(MockitoExtension.class)
class WorkerLeaseCoordinatorTest {

    @Mock
    private WorkerLeaseService workerLeaseService;

    @Test
    void disabledLease_isAlwaysLeaderWithoutDb() {
        WorkerLeaseCoordinator coordinator = new WorkerLeaseCoordinator(
                workerLeaseService, false, "indexer-leader", 15);
        coordinator.start();

        assertThat(coordinator.isLeader()).isTrue();
        verify(workerLeaseService, never()).tryAcquireOrRenew(anyString(), anyString(), any());
    }

    @Test
    void renewLease_promotesAndDemotes() {
        when(workerLeaseService.tryAcquireOrRenew(eq("indexer-leader"), anyString(), eq(Duration.ofSeconds(15))))
                .thenReturn(true, false);

        WorkerLeaseCoordinator coordinator = new WorkerLeaseCoordinator(
                workerLeaseService, true, "indexer-leader", 15);
        coordinator.start();
        assertThat(coordinator.isLeader()).isTrue();

        coordinator.renewLease();
        assertThat(coordinator.isLeader()).isFalse();
    }
}
