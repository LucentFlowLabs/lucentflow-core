package com.lucentflow.indexer.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Beans that replace indexer scan dependencies when the API replica is running
 * without {@code lucentflow.runtime.enable-indexer}.
 *
 * @author ArchLucent
 * @since 1.2
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ConditionalOnProperty(name = "lucentflow.runtime.enable-indexer", havingValue = "false")
public @interface ConditionalOnIndexerDisabled {
}
