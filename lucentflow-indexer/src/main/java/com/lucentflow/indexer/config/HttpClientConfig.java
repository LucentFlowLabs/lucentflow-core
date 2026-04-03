package com.lucentflow.indexer.config;

import okhttp3.ConnectionPool;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.OkHttp3ClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Configuration for HTTP clients and external API integrations.
 *
 * @author ArchLucent
 * @since 1.0
 */
@Configuration
public class HttpClientConfig {
    
    private static final String BASESCAN_USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36";

    /**
     * RestTemplate for Basescan API calls.
     */
    @Bean
    public RestTemplate restTemplate() {
        ConnectionPool connectionPool = new ConnectionPool(20, 5, TimeUnit.MINUTES);

        Interceptor browserImpersonationInterceptor = chain -> {
            Request original = chain.request();
            Request updated = original.newBuilder()
                    .header("User-Agent", BASESCAN_USER_AGENT)
                    .header("Accept", "application/json, text/plain, */*")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .header("Cache-Control", "no-cache")
                    .build();
            return chain.proceed(updated);
        };

        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .connectionPool(connectionPool)
                .addInterceptor(browserImpersonationInterceptor)
                .connectTimeout(Duration.ofSeconds(10))
                .readTimeout(Duration.ofSeconds(120))
                .writeTimeout(Duration.ofSeconds(10))
                .callTimeout(Duration.ofSeconds(120))
                .retryOnConnectionFailure(true)
                .build();

        OkHttp3ClientHttpRequestFactory factory = new OkHttp3ClientHttpRequestFactory(okHttpClient);
        return new RestTemplate(factory);
    }
}
