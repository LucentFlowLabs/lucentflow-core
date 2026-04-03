package com.lucentflow;

import com.lucentflow.common.config.AdaptiveEnvLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Prints CLI-oriented paths after the Spring Boot banner (on {@link ApplicationStartedEvent}).
 *
 * @author ArchLucent
 * @since 1.1
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CliStartupInfoListener implements ApplicationListener<ApplicationStartedEvent> {

    private static final Logger log = LoggerFactory.getLogger(CliStartupInfoListener.class);

    @Override
    public void onApplicationEvent(ApplicationStartedEvent event) {
        log.info("[CLI] Working directory: {}", System.getProperty("user.dir", "."));
        String envPath = AdaptiveEnvLoader.getLoadedEnvSourcePaths().isEmpty()
                ? "none"
                : String.join(" | ", AdaptiveEnvLoader.getLoadedEnvSourcePaths());
        log.info("[CLI] Resolved .env source paths: {}", envPath);
    }
}
