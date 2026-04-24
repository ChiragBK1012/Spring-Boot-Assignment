package com.grid07.api.service;

import com.grid07.api.config.RedisKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

/**
 * All Redis-based guardrails live here.
 *
 * Thread-safety strategy
 * ──────────────────────
 * The Horizontal Cap uses a Lua script executed atomically on the Redis server.
 * Lua scripts in Redis are guaranteed to run without interruption (single-threaded
 * execution model), so the INCR + compare is always race-condition-free even under
 * 200 concurrent JVM threads hitting the same key.
 *
 * The Cooldown Cap uses SET NX (set-if-not-exists) with a TTL in a single command,
 * which is also atomic.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GuardrailService {

    private static final int  BOT_HORIZONTAL_CAP   = 100;
    private static final long COOLDOWN_TTL_SECONDS  = 10 * 60L; // 10 minutes

    private final StringRedisTemplate redisTemplate;

    // -----------------------------------------------------------------------
    // Lua script: atomically increment bot_count and check cap in one round-trip.
    //
    // Returns:
    //   1  -> increment succeeded, new value is within cap
    //   0  -> cap already reached BEFORE this increment (no increment performed)
    //
    // The script rolls back (DECR) if the cap is exceeded so the counter
    // never goes above BOT_HORIZONTAL_CAP. This guarantees exactly 100 rows
    // even with 200 simultaneous requests.
    // -----------------------------------------------------------------------
    private static final DefaultRedisScript<Long> INCR_IF_UNDER_CAP_SCRIPT =
            new DefaultRedisScript<>("""
                    local current = redis.call('INCR', KEYS[1])
                    if current > tonumber(ARGV[1]) then
                        redis.call('DECR', KEYS[1])
                        return 0
                    end
                    return 1
                    """, Long.class);

    /**
     * Atomically checks horizontal cap and increments if allowed.
     *
     * @return true if the bot reply is allowed (counter was incremented)
     */
    public boolean tryIncrementBotCount(Long postId) {
        String key = RedisKeys.botCount(postId);
        Long result = redisTemplate.execute(
                INCR_IF_UNDER_CAP_SCRIPT,
                List.of(key),
                String.valueOf(BOT_HORIZONTAL_CAP)
        );
        boolean allowed = result != null && result == 1L;
        log.debug("[Guardrail:HorizontalCap] post={} allowed={}", postId, allowed);
        return allowed;
    }

    /**
     * Rolls back the bot counter (called when DB write fails after Redis increment).
     * Ensures Redis and DB never diverge.
     */
    public void decrementBotCount(Long postId) {
        redisTemplate.opsForValue().decrement(RedisKeys.botCount(postId));
    }

    /**
     * Checks depth-level vertical cap.
     *
     * @return true if allowed
     */
    public boolean checkVerticalCap(int depthLevel) {
        boolean allowed = depthLevel <= 20;
        if (!allowed) {
            log.debug("[Guardrail:VerticalCap] depthLevel={} BLOCKED", depthLevel);
        }
        return allowed;
    }

    /**
     * Checks and sets bot-human cooldown in one atomic SET NX EX.
     *
     * @return true if interaction is allowed (no existing cooldown key)
     */
    public boolean tryAcquireCooldown(Long botId, Long humanId) {
        String key = RedisKeys.cooldown(botId, humanId);
        // setIfAbsent = SET key 1 NX EX <ttl> — atomic in Redis
        Boolean set = redisTemplate.opsForValue()
                .setIfAbsent(key, "1", Duration.ofSeconds(COOLDOWN_TTL_SECONDS));
        boolean allowed = Boolean.TRUE.equals(set);
        log.debug("[Guardrail:Cooldown] bot={} human={} allowed={}", botId, humanId, allowed);
        return allowed;
    }

    /**
     * Returns current bot count for a post (for diagnostics).
     */
    public long getBotCount(Long postId) {
        String val = redisTemplate.opsForValue().get(RedisKeys.botCount(postId));
        return val == null ? 0L : Long.parseLong(val);
    }
}
