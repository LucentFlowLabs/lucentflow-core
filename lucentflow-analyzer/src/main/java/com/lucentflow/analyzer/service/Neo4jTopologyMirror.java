package com.lucentflow.analyzer.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * Optional Neo4j mirror hook for Genesis Trace 3.0.
 * Topology reads currently use Postgres {@code funding_edges}; enable this flag when a Neo4j
 * cluster is provisioned (see docker-compose neo4j service) for future Cypher export jobs.
 *
 * @author ArchLucent
 * @since 1.0
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "lucentflow.neo4j.enabled", havingValue = "true")
public class Neo4jTopologyMirror {

    private final String uri;

    public Neo4jTopologyMirror(@Value("${lucentflow.neo4j.uri:bolt://localhost:7687}") String uri) {
        this.uri = uri;
    }

    @PostConstruct
    void announce() {
        log.info("[NEO4J] Topology mirror enabled (uri={}). Postgres funding_edges remains the query source of truth.",
                uri);
    }
}
