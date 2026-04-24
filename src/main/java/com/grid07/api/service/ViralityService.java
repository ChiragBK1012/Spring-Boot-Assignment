package com.grid07.api.service;

import com.grid07.api.config.RedisKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ViralityService {

    private static final long BOT_REPLY_POINTS    = 1L;
    private static final long HUMAN_LIKE_POINTS   = 20L;
    private static final long HUMAN_COMMENT_POINTS = 50L;

    private final StringRedisTemplate redisTemplate;

    public void incrementForBotReply(Long postId) {
        increment(postId, BOT_REPLY_POINTS, "BOT_REPLY");
    }

    public void incrementForHumanLike(Long postId) {
        increment(postId, HUMAN_LIKE_POINTS, "HUMAN_LIKE");
    }

    public void incrementForHumanComment(Long postId) {
        increment(postId, HUMAN_COMMENT_POINTS, "HUMAN_COMMENT");
    }

    public Long getScore(Long postId) {
        String val = redisTemplate.opsForValue().get(RedisKeys.viralityScore(postId));
        return val == null ? 0L : Long.parseLong(val);
    }

    // -------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------

    private void increment(Long postId, long points, String reason) {
        String key = RedisKeys.viralityScore(postId);
        Long newScore = redisTemplate.opsForValue().increment(key, points);
        log.debug("[Virality] post={} reason={} +{} => total={}", postId, reason, points, newScore);
    }
}
