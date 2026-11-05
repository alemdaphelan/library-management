package com.huit.library.config;

import org.junit.jupiter.api.Test;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.assertj.core.api.Assertions.assertThat;

class NotificationDeliveryConfigTest {
    @Test void saturationNeitherRunsExternalIoOnCallerNorThrowsAfterCommit() throws Exception {
        var executor = new NotificationDeliveryConfig().notificationDeliveryExecutor();
        executor.setCorePoolSize(1); executor.setMaxPoolSize(1); executor.setQueueCapacity(1); executor.initialize();
        var started = new CountDownLatch(1); var release = new CountDownLatch(1); var queuedFinished = new CountDownLatch(1);
        var rejectedTaskRan = new AtomicBoolean(false);
        try {
            executor.execute(() -> {
                started.countDown();
                try { release.await(5, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            });
            assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
            executor.execute(queuedFinished::countDown);
            // A CallerRuns policy would execute this on the HTTP request; Abort would throw.
            executor.execute(() -> rejectedTaskRan.set(true));
            assertThat(rejectedTaskRan).isFalse();
            release.countDown(); assertThat(queuedFinished.await(2, TimeUnit.SECONDS)).isTrue();
        } finally { release.countDown(); executor.shutdown(); }
    }
}
