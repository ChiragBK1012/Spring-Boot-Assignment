package com.grid07.api.config;

/**
 * Centralised Redis key templates.
 * Using constants avoids typos and makes key patterns auditable in one place.
 */
public final class RedisKeys {

    private RedisKeys() {}

    // Phase 2 – Virality Engine
    public static String viralityScore(Long postId) {
        return "post:" + postId + ":virality_score";
    }

    // Phase 2 – Horizontal Cap (max 100 bot replies per post)
    public static String botCount(Long postId) {
        return "post:" + postId + ":bot_count";
    }

    // Phase 2 – Cooldown: bot cannot re-interact with same human within 10 min
    public static String cooldown(Long botId, Long humanId) {
        return "cooldown:bot_" + botId + ":human_" + humanId;
    }

    // Phase 3 – Notification throttle cooldown per user (15 min)
    public static String notifCooldown(Long userId) {
        return "notif:cooldown:user_" + userId;
    }

    // Phase 3 – Pending notification list per user
    public static String pendingNotifs(Long userId) {
        return "user:" + userId + ":pending_notifs";
    }

    // Pattern to scan all pending notification keys
    public static final String PENDING_NOTIFS_PATTERN = "user:*:pending_notifs";
}
