package com.huit.library.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** External delivery is best effort; the transactional in-app notification is authoritative. */
@Configuration
@EnableAsync
public class NotificationDeliveryConfig implements AsyncConfigurer {
    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationDeliveryConfig.class);

    @Bean("notificationDeliveryExecutor")
    public ThreadPoolTaskExecutor notificationDeliveryExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(256);
        executor.setThreadNamePrefix("notification-delivery-");
        executor.setDaemon(true);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        // Neither CallerRunsPolicy (blocking SMTP) nor AbortPolicy (HTTP error after commit)
        // is suitable here. The already-persisted in-app message remains available.
        executor.setRejectedExecutionHandler((task, pool) ->
                LOGGER.warn("External notification delivery queue unavailable; in-app notification is retained"));
        return executor;
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (error, method, arguments) -> LOGGER.warn("External notification delivery failed in {}: {}",
                method.getName(), error.getClass().getSimpleName());
    }
}
