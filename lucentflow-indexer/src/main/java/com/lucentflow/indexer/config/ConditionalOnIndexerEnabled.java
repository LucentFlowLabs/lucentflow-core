package com.lucentflow.indexer.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Scan / checkpoint stack. API profile sets {@code lucentflow.runtime.enable-indexer=false}
 * so this process cannot write {@code sync_status} id=1.
 *
 * @author ArchLucent
 * @since 1.2
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ConditionalOnProperty(name = "lucentflow.runtime.enable-indexer", havingValue = "true", matchIfMissing = true)
public @interface ConditionalOnIndexerEnabled {
}
