package com.lucentflow.api.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Enables admin MVC that must exist on both the API replica and the indexer worker
 * ({@code POST /api/v1/admin/backfill}). Product REST stays behind
 * {@link ConditionalOnApiEnabled}.
 *
 * @author ArchLucent
 * @since 1.2
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ConditionalOnExpression(
        "'${lucentflow.runtime.enable-api:true}' == 'true' or '${lucentflow.runtime.enable-indexer:true}' == 'true'")
public @interface ConditionalOnAdminSurfaceEnabled {
}
