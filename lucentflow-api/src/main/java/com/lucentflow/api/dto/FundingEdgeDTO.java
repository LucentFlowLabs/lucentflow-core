package com.lucentflow.api.dto;

/**
 * Funding topology edge DTO for Genesis Trace 3.0 queries.
 *
 * @author ArchLucent
 * @since 1.0
 */
public record FundingEdgeDTO(
        String funderAddress,
        String fundedAddress,
        Integer hopLayer,
        String relatedTxHash,
        String funderTag,
        Boolean blacklisted
) {
}
