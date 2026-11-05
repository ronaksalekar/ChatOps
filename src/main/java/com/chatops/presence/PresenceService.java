package com.chatops.presence;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Tracks online users and their individual WebSocket sessions in Redis. */
@Slf4j
@Service
@RequiredArgsConstructor
public class PresenceService {

    private static final String ONLINE_SET_KEY = "presence:online";
    private static final String SESSION_KEY_PREFIX = "presence:sessions:";
    private static final long PRESENCE_TTL_MS = Duration.ofSeconds(90).toMillis();

    private static final DefaultRedisScript<Long> ADD_SESSION = script("""
            local now = tonumber(ARGV[3])
            redis.call('ZREMRANGEBYSCORE', KEYS[2], '-inf', now)
            local count = redis.call('ZCARD', KEYS[2])
            redis.call('ZADD', KEYS[2], ARGV[2], ARGV[1])
            redis.call('SADD', KEYS[1], ARGV[4])
            if count == 0 then return 1 else return 0 end
            """);

    private static final DefaultRedisScript<Long> REMOVE_SESSION = script("""
            redis.call('ZREM', KEYS[2], ARGV[1])
            redis.call('ZREMRANGEBYSCORE', KEYS[2], '-inf', ARGV[2])
            if redis.call('ZCARD', KEYS[2]) == 0 then
              return redis.call('SREM', KEYS[1], ARGV[3])
            end
            return 0
            """);

    private static final DefaultRedisScript<Long> REFRESH_SESSION = script("""
            local now = tonumber(ARGV[2])
            redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', now)
            local count = redis.call('ZCARD', KEYS[1])
            redis.call('ZADD', KEYS[1], ARGV[3], ARGV[1])
            redis.call('SADD', KEYS[2], ARGV[4])
            if count == 0 then return 1 else return 0 end
            """);

    private static final DefaultRedisScript<Long> REFRESH_USER_SESSIONS = script("""
            local now = tonumber(ARGV[1])
            redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', now)
            local sessions = redis.call('ZRANGE', KEYS[1], 0, -1)
            for _, session in ipairs(sessions) do
              redis.call('ZADD', KEYS[1], ARGV[2], session)
            end
            return #sessions
            """);

    private static final DefaultRedisScript<Long> EVICT_EXPIRED = script("""
            redis.call('ZREMRANGEBYSCORE', KEYS[2], '-inf', ARGV[1])
            if redis.call('ZCARD', KEYS[2]) == 0 then
              redis.call('SREM', KEYS[1], ARGV[2])
              return 1
            end
            return 0
            """);

    private final StringRedisTemplate redisTemplate;

    /** Registers a session; returns true if this changed the user to online. */
    public boolean markOnline(String username, String sessionId) {
        long now = System.currentTimeMillis();
        Long becameOnline = redisTemplate.execute(ADD_SESSION,
                List.of(ONLINE_SET_KEY, sessionsKey(username)),
                sessionId, Long.toString(now + PRESENCE_TTL_MS), Long.toString(now), username);
        log.debug("Presence session connected: user={} session={}", username, sessionId);
        return Long.valueOf(1).equals(becameOnline);
    }

    /** Compatibility overload for callers without a WebSocket session ID. */
    @Deprecated
    public void markOnline(String username) {
        markOnline(username, "legacy:" + username);
        redisTemplate.opsForSet().add(ONLINE_SET_KEY, username);
        redisTemplate.opsForValue().set("presence:ttl:" + username, "1", Duration.ofSeconds(90));
    }

    /** Removes one session; returns true if this changed the user to offline. */
    public boolean markOffline(String username, String sessionId) {
        long now = System.currentTimeMillis();
        Long becameOffline = redisTemplate.execute(REMOVE_SESSION,
                List.of(ONLINE_SET_KEY, sessionsKey(username)),
                sessionId, Long.toString(now), username);
        log.debug("Presence session disconnected: user={} session={}", username, sessionId);
        return Long.valueOf(1).equals(becameOffline);
    }

    /** Compatibility overload paired with {@link #markOnline(String)}. */
    @Deprecated
    public void markOffline(String username) {
        markOffline(username, "legacy:" + username);
        redisTemplate.opsForSet().remove(ONLINE_SET_KEY, username);
        redisTemplate.delete("presence:ttl:" + username);
    }

    /** Renews a lease when a frame arrives on a specific STOMP session. */
    public boolean heartbeat(String username, String sessionId) {
        long now = System.currentTimeMillis();
        Long becameOnline = redisTemplate.execute(REFRESH_SESSION,
                List.of(sessionsKey(username), ONLINE_SET_KEY), sessionId,
                Long.toString(now), Long.toString(now + PRESENCE_TTL_MS), username);
        return Long.valueOf(1).equals(becameOnline);
    }

    /** REST heartbeat renews leases for the user's currently active sessions. */
    public void heartbeat(String username) {
        long now = System.currentTimeMillis();
        redisTemplate.execute(REFRESH_USER_SESSIONS, List.of(sessionsKey(username)),
                Long.toString(now), Long.toString(now + PRESENCE_TTL_MS));
        // Keep the old per-user heartbeat key readable during migration from the previous model.
        if (Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(ONLINE_SET_KEY, username))) {
            redisTemplate.opsForValue().set(
                    "presence:ttl:" + username, "1", Duration.ofMillis(PRESENCE_TTL_MS));
        }
    }

    public boolean isOnline(String username) {
        return Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(ONLINE_SET_KEY, username));
    }

    public Set<String> getAllOnlineUsers() {
        Set<String> members = redisTemplate.opsForSet().members(ONLINE_SET_KEY);
        return members != null ? members : Set.of();
    }

    public List<UserPresence> getBulkPresence(List<String> usernames) {
        return usernames.stream().map(u -> new UserPresence(u, isOnline(u))).toList();
    }

    /** Removes expired sessions and clears users with no live sessions. */
    public List<String> evictStalePresence() {
        long now = System.currentTimeMillis();
        List<String> evictedUsers = new ArrayList<>();
        for (String username : getAllOnlineUsers()) {
            Long evicted = redisTemplate.execute(EVICT_EXPIRED,
                    List.of(ONLINE_SET_KEY, sessionsKey(username)), Long.toString(now), username);
            if (Long.valueOf(1).equals(evicted)) {
                log.info("Evicted stale presence for user: {}", username);
                evictedUsers.add(username);
            }
        }
        return evictedUsers;
    }

    private String sessionsKey(String username) {
        return SESSION_KEY_PREFIX + username;
    }

    private static DefaultRedisScript<Long> script(String source) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(source);
        script.setResultType(Long.class);
        return script;
    }

    public record UserPresence(String username, boolean online) {}
}
