package com.grid07.api.service;

import com.grid07.api.config.RedisKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Phase 3 – Notification Engine
 *
 * Per-user throttle: if a user already received a push notification within the
 * last 15 minutes, new bot interactions are buffered into a Redis List instead
 * of being sent immediately. The CRON sweeper (NotificationScheduler) drains
 * these lists every 5 minutes and logs a summarised message.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final long NOTIF_COOLDOWN_SECONDS = 15 * 60L; // 15 minutes

    private final StringRedisTemplate redisTemplate;

    /**
     * Called whenever a bot interacts with a user's content.
     *
     * @param userId  the human who owns the post/comment
     * @param botName display name of the bot that interacted
     * @param postId  the post that was acted upon
     */
    public void handleBotInteraction(Long userId, String botName, Long postId) {
        String cooldownKey   = RedisKeys.notifCooldown(userId);
        String pendingKey    = RedisKeys.pendingNotifs(userId);
        String message       = botName + " replied to your post #" + postId;

        Boolean cooldownActive = redisTemplate.hasKey(cooldownKey);

        if (Boolean.TRUE.equals(cooldownActive)) {
            // User was recently notified — buffer the notification
            redisTemplate.opsForList().rightPush(pendingKey, message);
            log.debug("[Notification] userId={} buffered: {}", userId, message);
        } else {
            // No recent notification — send immediately and start cooldown
            log.info("[Notification] Push Notification Sent to User {}: {}", userId, message);
            redisTemplate.opsForValue()
                    .set(cooldownKey, "1", Duration.ofSeconds(NOTIF_COOLDOWN_SECONDS));
        }
    }
}
