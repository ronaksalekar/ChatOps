package com.chatops.presence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PresenceServiceTest {

    @Mock StringRedisTemplate redisTemplate;
    @Mock SetOperations<String, String> setOps;
    @Mock ValueOperations<String, String> valueOps;

    PresenceService presenceService;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        presenceService = new PresenceService(redisTemplate);
    }

    @Test
    void markOnline_addsToSetAndSetsTtl() {
        presenceService.markOnline("alice");

        verify(setOps).add("presence:online", "alice");
        verify(valueOps).set(eq("presence:ttl:alice"), eq("1"), any());
    }

    @Test
    void markOffline_removesFromSetAndDeletesTtl() {
        presenceService.markOffline("alice");

        verify(setOps).remove("presence:online", "alice");
        verify(redisTemplate).delete("presence:ttl:alice");
    }

    @Test
    void isOnline_returnsTrueWhenMemberExists() {
        when(setOps.isMember("presence:online", "alice")).thenReturn(true);

        assertThat(presenceService.isOnline("alice")).isTrue();
    }

    @Test
    void isOnline_returnsFalseWhenNotMember() {
        when(setOps.isMember("presence:online", "bob")).thenReturn(false);

        assertThat(presenceService.isOnline("bob")).isFalse();
    }

    @Test
    void getBulkPresence_returnsMixedStatuses() {
        when(setOps.isMember("presence:online", "alice")).thenReturn(true);
        when(setOps.isMember("presence:online", "bob")).thenReturn(false);

        List<PresenceService.UserPresence> result =
                presenceService.getBulkPresence(List.of("alice", "bob"));

        assertThat(result).containsExactly(
                new PresenceService.UserPresence("alice", true),
                new PresenceService.UserPresence("bob", false)
        );
    }

    @Test
    void heartbeat_refreshesTtlOnlyIfAlreadyOnline() {
        when(setOps.isMember("presence:online", "alice")).thenReturn(true);

        presenceService.heartbeat("alice");

        verify(valueOps).set(eq("presence:ttl:alice"), eq("1"), any());
    }

    @Test
    void heartbeat_doesNothingIfNotOnline() {
        when(setOps.isMember("presence:online", "ghost")).thenReturn(false);

        presenceService.heartbeat("ghost");

        verify(valueOps, never()).set(any(), any(), any());
    }
}
