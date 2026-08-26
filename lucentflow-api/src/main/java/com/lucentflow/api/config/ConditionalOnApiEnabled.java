package com.lucentflow.api.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Enables REST MVC / Admin product surfaces. Worker profile sets
 * {@code lucentflow.runtime.enable-api=false} so whales/forensics/projects stay off.
 * Indexer admin backfill is gated separately by {@link ConditionalOnAdminSurfaceEnabled}.
 *
 * @author ArchLucent
 * @since 1.2
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ConditionalOnProperty(name = "lucentflow.runtime.enable-api", havingValue = "true", matchIfMissing = true)
public @interface ConditionalOnApiEnabled {
}
