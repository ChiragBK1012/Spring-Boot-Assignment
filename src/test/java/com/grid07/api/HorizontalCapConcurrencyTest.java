package com.grid07.api;

import com.grid07.api.service.GuardrailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Concurrency test: 200 threads simultaneously try to increment the bot counter
 * for a single post. Exactly 100 should succeed (the rest are rejected by the
 * atomic Lua script). This is the Phase 4 Race-Condition / Spam Test.
 *
 * Requires a running Redis instance (use docker-compose up redis).
 */
@SpringBootTest
class HorizontalCapConcurrencyTest {

    @Autowired
    private GuardrailService guardrailService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private static final Long TEST_POST_ID = 999999L;

    @BeforeEach
    void cleanUp() {
        redisTemplate.delete("post:" + TEST_POST_ID + ":bot_count");
    }

    @Test
    void exactlyHundredBotRepliesAllowed_under200ConcurrentRequests() throws InterruptedException {
        int threads = 200;
        AtomicInteger allowed  = new AtomicInteger(0);
        AtomicInteger rejected = new AtomicInteger(0);

        ExecutorService pool  = Executors.newFixedThreadPool(threads);
        CountDownLatch  ready = new CountDownLatch(threads);
        CountDownLatch  start = new CountDownLatch(1);
        CountDownLatch  done  = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    ready.countDown();
                    start.await();          // all threads start simultaneously
                    if (guardrailService.tryIncrementBotCount(TEST_POST_ID)) {
                        allowed.incrementAndGet();
                    } else {
                        rejected.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await();   // wait until all threads are ready
        start.countDown(); // fire!
        done.await();    // wait for completion

        pool.shutdown();

        System.out.println("Allowed:  " + allowed.get());
        System.out.println("Rejected: " + rejected.get());

        assertThat(allowed.get()).isEqualTo(100);
        assertThat(rejected.get()).isEqualTo(100);
        assertThat(guardrailService.getBotCount(TEST_POST_ID)).isEqualTo(100L);
    }
}
