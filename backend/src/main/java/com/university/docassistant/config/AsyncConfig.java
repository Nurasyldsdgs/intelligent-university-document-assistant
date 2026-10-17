package com.university.docassistant.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Background AI analysis runs on its own bounded pool: a slow or hanging AI API can occupy at most
 * {@code app.ai.max-concurrency} threads, never the request threads. When the queue is full, submitting throws
 * TaskRejectedException, which callers handle (an upload then simply skips the analysis).
 */
@Configuration
@EnableAsync
@EnableConfigurationProperties({AiProperties.class, PrivacyProperties.class, OcrProperties.class})
public class AsyncConfig {

    public static final String AI_EXECUTOR = "aiTaskExecutor";

    @Bean(name = AI_EXECUTOR)
    public ThreadPoolTaskExecutor aiTaskExecutor(AiProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("ai-");
        executor.setCorePoolSize(properties.maxConcurrency());
        executor.setMaxPoolSize(properties.maxConcurrency());
        executor.setQueueCapacity(properties.queueCapacity());
        executor.setAllowCoreThreadTimeOut(true);
        return executor;
    }
}
