package com.lucentflow.sdk.config;

import okhttp3.ConnectionPool;
import okhttp3.Dispatcher;
import okhttp3.OkHttpClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import jakarta.annotation.PreDestroy;

/**
 * Spring Boot auto-configuration for Web3j with Java 21 virtual thread integration.
 * 
 * <p>Implementation Details:
 * Provides high-performance Web3j beans with virtual thread support for blockchain interactions.
 * Implements graceful fallback to traditional threads for Java versions below 21.
 * Configures optimized HTTP client with connection pooling and timeout management.
 * Virtual thread compatible through reflection-based virtual thread creation and stateless design.
 * </p>
 * 
 * @author ArchLucent
 * @since 1.0
 */
@Slf4j
@Configuration
@EnableAsync
@EnableConfigurationProperties(Web3jAutoConfiguration.Web3jProperties.class)
public class Web3jAutoConfiguration {
    
    private ScheduledExecutorService web3jExecutorService;

    /**
     * Creates a ScheduledExecutorService using Java 21 virtual threads for Web3j polling.
     * Leverages Project Loom virtual threads for lightweight, high-concurrency polling operations.
     * Falls back to traditional threads if virtual threads are not available.
     * 
     * @return Virtual thread-based ScheduledExecutorService for Web3j operations
     */
    @Bean
    public ScheduledExecutorService web3jExecutorService() {
        try {
            // Java 21+ virtual thread implementation
            ScheduledExecutorService executor = Executors.newScheduledThreadPool(1, 
                Thread.ofVirtual().name("web3j-scheduler-", 0).factory());
            log.info("Web3j virtual thread executor initialized successfully");
            this.web3jExecutorService = executor; // Store reference for shutdown
            return executor;
        } catch (Exception e) {
            // Fallback to traditional threads for Java < 21
            ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "web3j-scheduler-0");
                t.setDaemon(true);
                return t;
            });
            log.warn("Web3j falling back to traditional thread executor: {}", e.getMessage());
            this.web3jExecutorService = executor; // Store reference for shutdown
            return executor;
        }
    }
    
    @PreDestroy
    public void shutdownExecutor() {
        if (web3jExecutorService != null && !web3jExecutorService.isShutdown()) {
            log.info("Gracefully shutting down Web3j executor service");
            web3jExecutorService.shutdown();
            try {
                if (!web3jExecutorService.awaitTermination(10, TimeUnit.SECONDS)) {
                    log.warn("Web3j executor did not terminate gracefully within 10 seconds, forcing shutdown");
                    web3jExecutorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                log.warn("Interrupted while waiting for Web3j executor termination");
                Thread.currentThread().interrupt();
                web3jExecutorService.shutdownNow();
            }
            log.info("Web3j executor service shutdown completed");
        }
    }

    /**
     * Creates a virtual thread-per-task executor for async operations.
     * Ensures all @Async tasks (like Analysis Workers) run on lightweight virtual threads.
     * Falls back to cached thread pool if virtual threads are not available.
     * 
     * @return Virtual thread-per-task Executor for async operations
     */
    @Bean("taskExecutor")
    public Executor taskExecutor() {
        try {
            // Java 21+ virtual thread implementation using reflection
            return (Executor) Executors.class.getMethod("newVirtualThreadPerTaskExecutor").invoke(null);
        } catch (Exception e) {
            // Fallback to traditional thread pool for Java < 21
            return Executors.newCachedThreadPool(r -> {
                Thread t = new Thread(r, "async-task-");
                t.setDaemon(true);
                return t;
            });
        }
    }

    /**
     * Creates a custom OkHttpClient with proper timeouts and connection pooling for blockchain RPC calls.
     * Optimized for high-throughput blockchain operations with efficient connection management.
     * 
     * @return configured OkHttpClient
     */
    @Bean
    public RpcEndpointState rpcEndpointState(Web3jProperties web3jProperties) {
        return new RpcEndpointState(web3jProperties.getRpcUrl(), web3jProperties.getRpcBackupUrl());
    }

    @Bean
    public OkHttpClient okHttpClient(
            @Value("${PROXY_HOST:}") String proxyHost,
            @Value("${PROXY_PORT:}") String proxyPort,
            RpcEndpointState rpcEndpointState) {
        // DISABLED: HttpLoggingInterceptor causes I/O backpressure and deadlocks during high-frequency scanning
        // HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor();
        // loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BASIC);

        // Configure dispatcher for high throughput
        Dispatcher dispatcher = new Dispatcher();
        dispatcher.setMaxRequests(200);
        dispatcher.setMaxRequestsPerHost(100);

        // Configure connection pool
        ConnectionPool connectionPool = new ConnectionPool(50, 5, TimeUnit.MINUTES);

        Duration connectTimeout = Duration.ofSeconds(30);
        Duration readTimeout = Duration.ofSeconds(90);
        Duration writeTimeout = Duration.ofSeconds(30);
        Duration callTimeout = Duration.ofSeconds(120);

        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .addInterceptor(new RpcFailoverInterceptor(rpcEndpointState))
                .connectTimeout(connectTimeout)
                .readTimeout(readTimeout)
                .writeTimeout(writeTimeout)
                .callTimeout(callTimeout)
                .connectionPool(connectionPool)
                .dispatcher(dispatcher)
                .retryOnConnectionFailure(true);

        if (proxyHost != null && !proxyHost.isBlank() && proxyPort != null && !proxyPort.isBlank()) {
            try {
                int port = Integer.parseInt(proxyPort.trim());
                String host = proxyHost.trim();
                builder.proxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(host, port)));
                log.info("[HTTP-CLIENT] Outbound HTTP proxy enabled: {}:{}", host, port);
                if (isLoopbackProxyHost(host)) {
                    log.warn(
                            "[HTTP-CLIENT] PROXY_HOST is loopback ({}). In Docker containers 127.0.0.1/localhost is the container, "
                                    + "not your host VPN — RPC will fail with 'Failed to connect to /127.0.0.1:...'. "
                                    + "Use host.docker.internal (Docker Desktop) or unset PROXY_* if direct egress works.",
                            host);
                }
            } catch (NumberFormatException e) {
                log.warn("[HTTP-CLIENT] Invalid PROXY_PORT '{}', proxy disabled.", proxyPort);
            }
        }

        OkHttpClient client = builder.build();
        log.info(
                "[HTTP-CLIENT] OkHttp timeouts — connect={}ms read={}ms write={}ms call={}ms | "
                        + "connectionPool maxIdle=50 keepAlive=5m | dispatcher maxRequests={} maxRequestsPerHost={} | "
                        + "retryOnConnectionFailure=true",
                connectTimeout.toMillis(),
                readTimeout.toMillis(),
                writeTimeout.toMillis(),
                callTimeout.toMillis(),
                client.dispatcher().getMaxRequests(),
                client.dispatcher().getMaxRequestsPerHost());
        return client;
    }

    /**
     * Creates a Web3j bean configured with virtual thread executor and custom HTTP client.
     * Uses the correct Web3j.build() method with polling interval and virtual thread executor
     * for optimal resource utilization in Java 21 environments.
     * 
     * @param web3jProperties configuration properties
     * @param web3jExecutorService virtual thread-based executor service
     * @param okHttpClient custom HTTP client
     * @return configured Web3j instance with virtual thread support
     */
    @Bean
    public Web3j web3j(Web3jProperties web3jProperties, 
                      ScheduledExecutorService web3jExecutorService,
                      OkHttpClient okHttpClient) {
        HttpService httpService = new HttpService(web3jProperties.getRpcUrl(), okHttpClient, false);
        
        // Build Web3j with virtual thread executor for async operations (5000ms polling interval)
        return Web3j.build(httpService, 5000, web3jExecutorService);
    }

    /**
     * Smart dual-channel RPC: derives semaphore permits, pipeline chunk size, and inter-batch sleep
     * from the configured endpoint URL (professional vs public).
     *
     * @param properties bound {@code lucentflow.chain} properties (including {@code rpc-url})
     * @return immutable recommended limits for indexer components
     */
    @Bean
    public RpcProviderConfig rpcProviderConfig(
            Web3jProperties properties,
            @Value("${lucentflow.chain.professional-inter-batch-sleep-ms:2000}") long professionalInterBatchSleepMs) {
        RpcProviderType type = RpcProviderType.fromRpcUrl(properties.getRpcUrl());
        long proSleep = Math.max(0L, professionalInterBatchSleepMs);
        // Alchemy / QuickNode / Infura / BlastAPI: conservative free-tier throttling.
        RpcProviderConfig cfg = switch (type) {
            // Professional endpoints: 8 permits is the Alchemy Free-Tier sweet spot
            // (330 CUPS budget / ~40 CUPS per block = safe ceiling for sustained syncs).
            // Inter-batch delay is tunable via lucentflow.chain.professional-inter-batch-sleep-ms (default 2000ms).
            case PROFESSIONAL -> new RpcProviderConfig(type, 8, 100, proSleep);
            case PUBLIC -> new RpcProviderConfig(type, 2, 50, 3000L);
        };
        log.info(
                "[RPC-POLICY] RpcProviderConfig tier={} recommendedRpcSemaphorePermits={} recommendedPipelineChunk={} interBatchSleepMs={}. "
                        + "Indexer *effective* concurrency is lucentflow.indexer.max-concurrency (IndexerRpcProfile / RpcConcurrencyGovernor), "
                        + "not this bean's recommendedPermits — align env LUCENTFLOW_INDEXER_MAX_CONCURRENCY with your RPC plan.",
                cfg.providerType(),
                cfg.recommendedRpcSemaphorePermits(),
                cfg.recommendedPipelineChunkSize(),
                cfg.interBatchSleepMillis());
        return cfg;
    }

    private static boolean isLoopbackProxyHost(String host) {
        return "127.0.0.1".equals(host) || "::1".equals(host) || "localhost".equalsIgnoreCase(host);
    }

    /**
     * Configuration properties for Web3j connection settings.
     */
    @ConfigurationProperties(prefix = "lucentflow.chain")
    public static class Web3jProperties {
        private String rpcUrl;
        /** Fallback JSON-RPC URL when primary returns 4xx/5xx or I/O failure (5-minute cooldown). */
        private String rpcBackupUrl = "https://mainnet.base.org";

        public String getRpcUrl() {
            return rpcUrl;
        }

        public void setRpcUrl(String rpcUrl) {
            this.rpcUrl = rpcUrl;
        }

        public String getRpcBackupUrl() {
            return rpcBackupUrl;
        }

        public void setRpcBackupUrl(String rpcBackupUrl) {
            this.rpcBackupUrl = rpcBackupUrl;
        }
    }
}
