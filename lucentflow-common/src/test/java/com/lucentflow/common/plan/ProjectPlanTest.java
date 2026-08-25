package com.lucentflow.common.plan;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Commercial plan defaults used by project create/update.
 *
 * @author ArchLucent
 * @since 1.2
 */
class ProjectPlanTest {

    @Test
    void normalize_defaultsBlankToBuilder() {
        assertThat(ProjectPlan.normalize(null)).isEqualTo(ProjectPlan.BUILDER);
        assertThat(ProjectPlan.normalize(" desk ")).isEqualTo(ProjectPlan.DESK);
    }

    @Test
    void normalize_rejectsUnknownPlan() {
        assertThatThrownBy(() -> ProjectPlan.normalize("GOLD"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void builderDefaults_matchCommercialAnchors() {
        assertThat(ProjectPlan.defaultDailyRequestQuota(ProjectPlan.BUILDER)).isEqualTo(2_000);
        assertThat(ProjectPlan.defaultWatchlistLimit(ProjectPlan.BUILDER)).isEqualTo(50);
        assertThat(ProjectPlan.defaultWatchlistLimit(ProjectPlan.PROTOCOL)).isEqualTo(1_000);
    }
}
