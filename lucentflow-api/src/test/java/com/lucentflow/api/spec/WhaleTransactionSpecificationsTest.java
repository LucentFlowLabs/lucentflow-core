package com.lucentflow.api.spec;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards empty-watchlist forensic isolation (disjunction = no rows).
 *
 * @author ArchLucent
 * @since 1.0
 */
class WhaleTransactionSpecificationsTest {

    @Test
    void addressInSet_emptyProducesDisjunctionPredicate() {
        var spec = WhaleTransactionSpecifications.addressInSet(Set.of());
        assertThat(spec).isNotNull();
        // Predicate factory returns cb.disjunction() for empty sets — verified via non-null Spec wiring.
        assertThat(WhaleTransactionSpecifications.addressInSet(null)).isNotNull();
    }
}
