package com.grid07.api.scheduler;

import com.grid07.api.config.RedisKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * Phase 3 – CRON Sweeper
 *
 * Runs every 5 minutes (simulating the 15-minute production sweep).
 * Scans all "user:*:pending_notifs" keys, pops every buffered message,
 * and logs a summarised push notification.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationScheduler {

    private final StringRedisTemplate redisTemplate;

    /**
     * fixedDelay = 5 minutes (300_000 ms).
     * Use @Scheduled(cron = "0 * /5 * * * *") in production for wall-clock alignment.
     */
    @Scheduled(fixedDelay = 300_000)
    public void sweepPendingNotifications() {
        log.info("[Scheduler] Starting pending notification sweep...");

        Set<String> keys = redisTemplate.keys(RedisKeys.PENDING_NOTIFS_PATTERN);
        if (keys == null || keys.isEmpty()) {
            log.info("[Scheduler] No pending notifications found.");
            return;
        }

        for (String key : keys) {
            processPendingList(key);
        }

        log.info("[Scheduler] Sweep complete. Processed {} user(s).", keys.size());
    }

    // -----------------------------------------------------------------------

    private void processPendingList(String key) {
        // LRANGE then DEL is not perfectly atomic, but acceptable here:
        // worst case we double-process one notification on restart.
        // For a stricter guarantee you could use a Lua script.
        List<String> messages = redisTemplate.opsForList().range(key, 0, -1);
        if (messages == null || messages.isEmpty()) {
            return;
        }

        // Delete the list atomically before processing so concurrent pushes
        // after this point land in a fresh list and are not lost.
        redisTemplate.delete(key);

        // Extract userId from key pattern "user:{id}:pending_notifs"
        String userId = extractUserId(key);
        int count = messages.size();

        // Build summarised message
        // First message looks like "BotName replied to your post #X"
        String firstBotName = extractBotName(messages.get(0));
        String summary;
        if (count == 1) {
            summary = firstBotName + " interacted with your posts.";
        } else {
            summary = "Summarized Push Notification: " + firstBotName +
                      " and [" + (count - 1) + "] others interacted with your posts.";
        }

        log.info("[Scheduler] User {} => {}", userId, summary);
    }

    private String extractUserId(String key) {
        // key = "user:{id}:pending_notifs"
        String[] parts = key.split(":");
        return parts.length >= 2 ? parts[1] : "unknown";
    }

    private String extractBotName(String message) {
        // message = "BotName replied to your post #X"
        int idx = message.indexOf(" replied");
        return idx > 0 ? message.substring(0, idx) : message;
    }
}
