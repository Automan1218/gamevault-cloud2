package com.sg.nusiss.social.service;

import com.sg.nusiss.social.dto.message.response.MessageResponse;
import com.sg.nusiss.social.service.cache.MessageCacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * @ClassName MessageCacheServiceTest
 * @Author HUANG ZHENJIA
 * @Date 2025/11/14
 * @Description MessageCacheService 单元测试
 */
@ExtendWith(MockitoExtension.class)
class MessageCacheServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ListOperations<String, Object> listOperations;

    @InjectMocks
    private MessageCacheService messageCacheService;

    private MessageResponse testMessage;
    private static final String MESSAGE_CACHE_PREFIX = "chat:messages:";
    private static final int CACHE_SIZE = 100;
    private static final long CACHE_EXPIRE_DAYS = 7;

    @BeforeEach
    void setUp() {
        testMessage = MessageResponse.builder()
                .id(1L)
                .conversationId(100L)
                .senderId(1L)
                .senderUsername("testUser")
                .content("Hello World")
                .messageType("text")
                .createdAt(LocalDateTime.now())
                .build();
    }

    // ==================== cacheMessage 方法测试 ====================

    @Test
    void testCacheMessage_Success() {
        // Given
        String key = MESSAGE_CACHE_PREFIX + "100";

        when(redisTemplate.opsForList()).thenReturn(listOperations);
        when(listOperations.rightPush(key, testMessage)).thenReturn(1L);
        when(listOperations.size(key)).thenReturn(1L);
        when(redisTemplate.expire(key, CACHE_EXPIRE_DAYS, TimeUnit.DAYS)).thenReturn(true);

        // When
        messageCacheService.cacheMessage(testMessage);

        // Then
        verify(listOperations, times(1)).rightPush(key, testMessage);
        verify(listOperations, times(1)).size(key);
        verify(redisTemplate, times(1)).expire(key, CACHE_EXPIRE_DAYS, TimeUnit.DAYS);
        verify(listOperations, never()).leftPop(anyString());
    }

    @Test
    void testCacheMessage_ExceedLimit_RemoveOldest() {
        // Given
        String key = MESSAGE_CACHE_PREFIX + "100";

        when(redisTemplate.opsForList()).thenReturn(listOperations);
        when(listOperations.rightPush(key, testMessage)).thenReturn(101L);
        when(listOperations.size(key)).thenReturn(101L);
        when(listOperations.leftPop(key)).thenReturn(new Object());
        when(redisTemplate.expire(key, CACHE_EXPIRE_DAYS, TimeUnit.DAYS)).thenReturn(true);

        // When
        messageCacheService.cacheMessage(testMessage);

        // Then
        verify(listOperations, times(1)).rightPush(key, testMessage);
        verify(listOperations, times(1)).size(key);
        verify(listOperations, times(1)).leftPop(key);
        verify(redisTemplate, times(1)).expire(key, CACHE_EXPIRE_DAYS, TimeUnit.DAYS);
    }

    @Test
    void testCacheMessage_RedisException_NoThrow() {
        // Given
        String key = MESSAGE_CACHE_PREFIX + "100";

        when(redisTemplate.opsForList()).thenReturn(listOperations);
        when(listOperations.rightPush(key, testMessage)).thenThrow(new RuntimeException("Redis error"));

        // When & Then
        assertDoesNotThrow(() -> {
            messageCacheService.cacheMessage(testMessage);
        });

        verify(listOperations, times(1)).rightPush(key, testMessage);
    }

    // ==================== getCachedMessages 方法测试 ====================

    @Test
    void testGetCachedMessages_Success() {
        // Given
        Long conversationId = 100L;
        int limit = 10;
        String key = MESSAGE_CACHE_PREFIX + conversationId;

        MessageResponse msg1 = MessageResponse.builder().id(1L).conversationId(100L).content("Msg 1").build();
        MessageResponse msg2 = MessageResponse.builder().id(2L).conversationId(100L).content("Msg 2").build();
        List<Object> cachedMessages = Arrays.asList(msg1, msg2);

        when(redisTemplate.opsForList()).thenReturn(listOperations);
        when(listOperations.size(key)).thenReturn(2L);
        when(listOperations.range(key, 0, -1)).thenReturn(cachedMessages);

        // When
        List<MessageResponse> result = messageCacheService.getCachedMessages(conversationId, limit);

        // Then
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("Msg 1", result.get(0).getContent());
        assertEquals("Msg 2", result.get(1).getContent());

        verify(listOperations, times(1)).size(key);
        verify(listOperations, times(1)).range(key, 0, -1);
    }

    @Test
    void testGetCachedMessages_EmptyCache_ReturnEmpty() {
        // Given
        Long conversationId = 100L;
        String key = MESSAGE_CACHE_PREFIX + conversationId;

        when(redisTemplate.opsForList()).thenReturn(listOperations);
        when(listOperations.size(key)).thenReturn(0L);

        // When
        List<MessageResponse> result = messageCacheService.getCachedMessages(conversationId, 10);

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());

        verify(listOperations, times(1)).size(key);
        verify(listOperations, never()).range(anyString(), anyLong(), anyLong());
    }

    @Test
    void testGetCachedMessages_NullSize_ReturnEmpty() {
        // Given
        Long conversationId = 100L;
        String key = MESSAGE_CACHE_PREFIX + conversationId;

        when(redisTemplate.opsForList()).thenReturn(listOperations);
        when(listOperations.size(key)).thenReturn(null);

        // When
        List<MessageResponse> result = messageCacheService.getCachedMessages(conversationId, 10);

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());

        verify(listOperations, times(1)).size(key);
    }

    @Test
    void testGetCachedMessages_WithLimit_ReturnLastN() {
        // Given
        Long conversationId = 100L;
        int limit = 5;
        String key = MESSAGE_CACHE_PREFIX + conversationId;

        List<Object> cachedMessages = Arrays.asList(
                MessageResponse.builder().id(6L).build(),
                MessageResponse.builder().id(7L).build(),
                MessageResponse.builder().id(8L).build(),
                MessageResponse.builder().id(9L).build(),
                MessageResponse.builder().id(10L).build()
        );

        when(redisTemplate.opsForList()).thenReturn(listOperations);
        when(listOperations.size(key)).thenReturn(10L);
        when(listOperations.range(key, 5, -1)).thenReturn(cachedMessages);

        // When
        List<MessageResponse> result = messageCacheService.getCachedMessages(conversationId, limit);

        // Then
        assertNotNull(result);
        assertEquals(5, result.size());

        verify(listOperations, times(1)).range(key, 5, -1);
    }

    @Test
    void testGetCachedMessages_RedisException_ReturnEmpty() {
        // Given
        String key = MESSAGE_CACHE_PREFIX + "100";

        when(redisTemplate.opsForList()).thenReturn(listOperations);
        when(listOperations.size(key)).thenThrow(new RuntimeException("Redis error"));

        // When
        List<MessageResponse> result = messageCacheService.getCachedMessages(100L, 10);

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // ==================== batchCacheMessages 方法测试 ====================

    @Test
    void testBatchCacheMessages_Success() {
        // Given
        Long conversationId = 100L;
        String key = MESSAGE_CACHE_PREFIX + conversationId;

        MessageResponse msg1 = MessageResponse.builder().id(1L).conversationId(100L).build();
        MessageResponse msg2 = MessageResponse.builder().id(2L).conversationId(100L).build();
        List<MessageResponse> messages = Arrays.asList(msg1, msg2);

        when(redisTemplate.delete(key)).thenReturn(true);
        when(redisTemplate.opsForList()).thenReturn(listOperations);
        when(listOperations.rightPush(eq(key), any(MessageResponse.class))).thenReturn(1L);
        when(redisTemplate.expire(key, CACHE_EXPIRE_DAYS, TimeUnit.DAYS)).thenReturn(true);

        // When
        messageCacheService.batchCacheMessages(conversationId, messages);

        // Then
        verify(redisTemplate, times(1)).delete(key);
        verify(listOperations, times(2)).rightPush(eq(key), any(MessageResponse.class));
        verify(redisTemplate, times(1)).expire(key, CACHE_EXPIRE_DAYS, TimeUnit.DAYS);
    }

    @Test
    void testBatchCacheMessages_EmptyList_NoAction() {
        // Given
        Long conversationId = 100L;

        // When
        messageCacheService.batchCacheMessages(conversationId, Arrays.asList());

        // Then
        verify(redisTemplate, never()).delete(anyString());
        verify(redisTemplate, never()).opsForList();
    }

    @Test
    void testBatchCacheMessages_NullList_NoAction() {
        // Given
        Long conversationId = 100L;

        // When
        messageCacheService.batchCacheMessages(conversationId, null);

        // Then
        verify(redisTemplate, never()).delete(anyString());
        verify(redisTemplate, never()).opsForList();
    }

    @Test
    void testBatchCacheMessages_ExceedCacheSize_OnlyCacheLast() {
        // Given
        Long conversationId = 100L;
        String key = MESSAGE_CACHE_PREFIX + conversationId;

        List<MessageResponse> messages = new java.util.ArrayList<>();
        for (int i = 1; i <= 150; i++) {
            messages.add(MessageResponse.builder().id((long) i).conversationId(100L).build());
        }

        when(redisTemplate.delete(key)).thenReturn(true);
        when(redisTemplate.opsForList()).thenReturn(listOperations);
        when(listOperations.rightPush(eq(key), any(MessageResponse.class))).thenReturn(1L);
        when(redisTemplate.expire(key, CACHE_EXPIRE_DAYS, TimeUnit.DAYS)).thenReturn(true);

        // When
        messageCacheService.batchCacheMessages(conversationId, messages);

        // Then
        verify(redisTemplate, times(1)).delete(key);
        verify(listOperations, times(100)).rightPush(eq(key), any(MessageResponse.class));
        verify(redisTemplate, times(1)).expire(key, CACHE_EXPIRE_DAYS, TimeUnit.DAYS);
    }

    @Test
    void testBatchCacheMessages_RedisException_NoThrow() {
        // Given
        String key = MESSAGE_CACHE_PREFIX + "100";
        List<MessageResponse> messages = Arrays.asList(testMessage);

        when(redisTemplate.delete(key)).thenThrow(new RuntimeException("Redis error"));

        // When & Then
        assertDoesNotThrow(() -> {
            messageCacheService.batchCacheMessages(100L, messages);
        });
    }

    // ==================== clearCache 方法测试 ====================

    @Test
    void testClearCache_Success() {
        // Given
        Long conversationId = 100L;
        String key = MESSAGE_CACHE_PREFIX + conversationId;

        when(redisTemplate.delete(key)).thenReturn(true);

        // When
        messageCacheService.clearCache(conversationId);

        // Then
        verify(redisTemplate, times(1)).delete(key);
    }

    @Test
    void testClearCache_RedisException_NoThrow() {
        // Given
        String key = MESSAGE_CACHE_PREFIX + "100";

        when(redisTemplate.delete(key)).thenThrow(new RuntimeException("Redis error"));

        // When & Then
        assertDoesNotThrow(() -> {
            messageCacheService.clearCache(100L);
        });
    }
}