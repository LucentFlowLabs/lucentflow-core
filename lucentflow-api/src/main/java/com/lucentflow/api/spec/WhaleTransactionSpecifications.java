package com.lucentflow.api.spec;

import com.lucentflow.common.entity.WhaleTransaction;
import org.springframework.data.jpa.domain.Specification;

/**
 * Specification factory for forensic whale event filters.
 *
 * @author ArchLucent
 * @since 1.0
 */
public final class WhaleTransactionSpecifications {

    private WhaleTransactionSpecifications() {
    }

    public static Specification<WhaleTransaction> minRiskScore(Integer minRiskScore) {
        return (root, query, cb) -> minRiskScore == null ? null : cb.greaterThanOrEqualTo(root.get("riskScore"), minRiskScore);
    }

    public static Specification<WhaleTransaction> maxRiskScore(Integer maxRiskScore) {
        return (root, query, cb) -> maxRiskScore == null ? null : cb.lessThanOrEqualTo(root.get("riskScore"), maxRiskScore);
    }

    public static Specification<WhaleTransaction> address(String address) {
        return (root, query, cb) -> {
            if (address == null || address.isBlank()) {
                return null;
            }
            String normalized = address.trim().toLowerCase();
            return cb.or(
                    cb.equal(cb.lower(root.get("fromAddress")), normalized),
                    cb.equal(cb.lower(root.get("toAddress")), normalized)
            );
        };
    }

    public static Specification<WhaleTransaction> bytecodeHash(String bytecodeHash) {
        return (root, query, cb) -> {
            if (bytecodeHash == null || bytecodeHash.isBlank()) {
                return null;
            }
            return cb.equal(cb.lower(root.get("bytecodeHash")), bytecodeHash.trim().toLowerCase());
        };
    }

    public static Specification<WhaleTransaction> reasonContains(String reason) {
        return (root, query, cb) -> {
            if (reason == null || reason.isBlank()) {
                return null;
            }
            String likePattern = "%" + reason.trim().toLowerCase() + "%";
            return cb.like(cb.lower(root.get("riskReasons").as(String.class)), likePattern);
        };
    }
}
