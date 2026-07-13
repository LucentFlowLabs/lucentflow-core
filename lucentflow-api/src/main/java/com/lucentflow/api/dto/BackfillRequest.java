package com.lucentflow.api.dto;

/**
 * Admin historical backfill request.
 *
 * @author ArchLucent
 * @since 1.0
 */
public record BackfillRequest(Long fromBlock, Long toBlock) {
}
