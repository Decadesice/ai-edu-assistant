package com.syh.chat.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RateLimitService {

    private static final DefaultRedisScript<Long> FIXED_WINDOW_SCRIPT = new DefaultRedisScript<>(
            "local current = redis.call('INCR', KEYS[1])\n" +
                    "if current == 1 then redis.call('EXPIRE', KEYS[1], ARGV[2]) end\n" +
                    "if current > tonumber(ARGV[1]) then return 0 else return 1 end\n",
            Long.class
    );

    private final StringRedisTemplate redisTemplate;

    public RateLimitService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public boolean allow(String key, int limit, int windowSeconds) {
        Long allowed = redisTemplate.execute(
                FIXED_WINDOW_SCRIPT,
                List.of(key),
                String.valueOf(limit),
                String.valueOf(windowSeconds)
        );
        return allowed != null && allowed == 1L;
    }
}
