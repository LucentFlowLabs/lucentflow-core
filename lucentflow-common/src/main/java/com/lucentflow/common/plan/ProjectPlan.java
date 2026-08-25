package com.lucentflow.common.plan;

import java.util.Locale;
import java.util.Set;

/**
 * Commercial project tiers and their default quotas.
 *
 * @author ArchLucent
 * @since 1.2
 */
public final class ProjectPlan {

    public static final String BUILDER = "BUILDER";
    public static final String DESK = "DESK";
    public static final String PROTOCOL = "PROTOCOL";

    private static final Set<String> KNOWN = Set.of(BUILDER, DESK, PROTOCOL);

    private ProjectPlan() {
    }

    public static String normalize(String plan) {
        if (plan == null || plan.isBlank()) {
            return BUILDER;
        }
        String normalized = plan.trim().toUpperCase(Locale.ROOT);
        if (!KNOWN.contains(normalized)) {
            throw new IllegalArgumentException("Unknown project plan: " + plan);
        }
        return normalized;
    }

    public static int defaultDailyRequestQuota(String plan) {
        return switch (normalize(plan)) {
            case DESK -> 20_000;
            case PROTOCOL -> 100_000;
            default -> 2_000;
        };
    }

    public static int defaultWatchlistLimit(String plan) {
        return switch (normalize(plan)) {
            case DESK -> 200;
            case PROTOCOL -> 1_000;
            default -> 50;
        };
    }
}
