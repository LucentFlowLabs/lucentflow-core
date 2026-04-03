package com.lucentflow.common.config;

import io.github.cdimascio.dotenv.Dotenv;
import io.github.cdimascio.dotenv.DotenvEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Greedy multi-path {@code .env} loading and JVM adaptation before Spring Boot starts.
 * Merges files in order: first file wins on duplicate keys ({@link Map#putIfAbsent});
 * does not override existing OS env or system properties when applying to {@link System#setProperty}.
 *
 * @author ArchLucent
 * @since 1.1
 */
public final class AdaptiveEnvLoader {

    private static final Logger log = LoggerFactory.getLogger(AdaptiveEnvLoader.class);

    private static final String LOCAL_PROFILE = "local";
    private static final String DOCKER_INTERNAL_HOST = "host.docker.internal";
    private static final String LOOPBACK = "127.0.0.1";

    /**
     * Merge order (first file wins on duplicate keys): root {@code .env}, then standard docker template,
     * then parent-relative docker path when running from a nested working directory.
     */
    private static final String[] RELATIVE_ENV_PATHS = {
            ".env",
            "lucentflow-deployment/docker/.env",
            "../lucentflow-deployment/docker/.env"
    };

    private static final List<String> LAST_LOADED_ENV_SOURCES = new CopyOnWriteArrayList<>();

    private AdaptiveEnvLoader() {
    }

    /**
     * Absolute paths of {@code .env} files merged during {@link #loadEnv()} (may be empty).
     */
    public static List<String> getLoadedEnvSourcePaths() {
        return Collections.unmodifiableList(new ArrayList<>(LAST_LOADED_ENV_SOURCES));
    }

    /**
     * Load merged {@code .env} files, apply system properties, default profile when appropriate,
     * map JVM HTTP(S) proxy settings, and emit a single boot summary line (no secrets).
     */
    public static void loadEnv() {
        Path userDir = Path.of(System.getProperty("user.dir", ".")).normalize();
        Map<String, String> merged = new LinkedHashMap<>();
        List<String> sources = new ArrayList<>();

        for (String rel : RELATIVE_ENV_PATHS) {
            Path file = userDir.resolve(rel).normalize();
            if (!Files.isRegularFile(file)) {
                continue;
            }
            Dotenv block = Dotenv.configure()
                    .directory(file.getParent().toString())
                    .filename(file.getFileName().toString())
                    .ignoreIfMissing()
                    .load();
            for (DotenvEntry e : block.entries()) {
                merged.putIfAbsent(e.getKey(), Objects.toString(e.getValue(), ""));
            }
            sources.add(file.toString());
        }

        LAST_LOADED_ENV_SOURCES.clear();
        LAST_LOADED_ENV_SOURCES.addAll(sources);

        for (Map.Entry<String, String> e : merged.entrySet()) {
            applyPropertyIfAbsent(e.getKey(), e.getValue());
        }

        applyDefaultProfileIfNeeded();

        String effectiveProfile = resolveActiveProfileLabel();

        ProxySnapshot proxy = applyJvmProxyAndResolve(effectiveProfile);

        String sourceLabel = sources.isEmpty() ? "none" : String.join(" | ", sources);
        log.info(
                "[BOOT] 🚀 Adaptive Environment active. Source: {}, Profile: {}, Proxy: {}:{}, Thread-Model: VirtualThreads",
                sourceLabel,
                effectiveProfile,
                proxy.hostLabel(),
                proxy.portLabel()
        );
    }

    private static void applyPropertyIfAbsent(String key, String value) {
        if (key == null || key.isBlank()) {
            return;
        }
        if (System.getenv(key) != null) {
            return;
        }
        if (System.getProperty(key) != null) {
            return;
        }
        System.setProperty(key, value);
    }

    private static void applyDefaultProfileIfNeeded() {
        String active = firstNonBlank(
                System.getenv("SPRING_PROFILES_ACTIVE"),
                System.getProperty("SPRING_PROFILES_ACTIVE"),
                System.getProperty("spring.profiles.active")
        );
        if (active != null && !active.isBlank()) {
            return;
        }
        if (isRunningInContainer()) {
            return;
        }
        System.setProperty("spring.profiles.active", LOCAL_PROFILE);
    }

    private static String resolveActiveProfileLabel() {
        String p = firstNonBlank(
                System.getenv("SPRING_PROFILES_ACTIVE"),
                System.getProperty("SPRING_PROFILES_ACTIVE"),
                System.getProperty("spring.profiles.active")
        );
        if (p == null || p.isBlank()) {
            return "(default)";
        }
        return p;
    }

    /**
     * Heuristic container detection (Linux). On Windows/macOS hosts without /proc, returns false.
     */
    static boolean isRunningInContainer() {
        if (Files.exists(Path.of("/.dockerenv"))) {
            return true;
        }
        Path cgroup = Path.of("/proc/1/cgroup");
        if (!Files.isRegularFile(cgroup)) {
            return false;
        }
        try {
            String content = Files.readString(cgroup).toLowerCase(Locale.ROOT);
            return content.contains("docker")
                    || content.contains("kubepods")
                    || content.contains("containerd");
        } catch (IOException e) {
            return false;
        }
    }

    private static ProxySnapshot applyJvmProxyAndResolve(String effectiveProfile) {
        String host = firstNonBlank(System.getenv("PROXY_HOST"), System.getProperty("PROXY_HOST"));
        String port = firstNonBlank(System.getenv("PROXY_PORT"), System.getProperty("PROXY_PORT"));

        boolean localLike = effectiveProfile.toLowerCase(Locale.ROOT).contains(LOCAL_PROFILE);
        if (localLike && host != null && DOCKER_INTERNAL_HOST.equalsIgnoreCase(host.trim())) {
            host = LOOPBACK;
            System.setProperty("PROXY_HOST", host);
        }

        if (host != null && !host.isBlank() && port != null && !port.isBlank()) {
            System.setProperty("http.proxyHost", host.trim());
            System.setProperty("https.proxyHost", host.trim());
            System.setProperty("http.proxyPort", port.trim());
            System.setProperty("https.proxyPort", port.trim());
            return new ProxySnapshot(host.trim(), port.trim());
        }
        return new ProxySnapshot("-", "-");
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        if (b != null && !b.isBlank()) {
            return b;
        }
        return null;
    }

    private static String firstNonBlank(String a, String b, String c) {
        String x = firstNonBlank(a, b);
        if (x != null && !x.isBlank()) {
            return x;
        }
        if (c != null && !c.isBlank()) {
            return c;
        }
        return null;
    }

    private record ProxySnapshot(String hostLabel, String portLabel) {
    }
}
