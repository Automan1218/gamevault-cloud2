package com.sg.nusiss.social.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sg.nusiss.common.dto.UserDTO;
import com.sg.nusiss.social.service.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private UserService userService;

    private UserDTO testUser;
    private static final String CACHE_PREFIX = "user:";
    private static final long CACHE_EXPIRE_HOURS = 1;
    private static final String AUTH_SERVICE_URL = "http://gamevault-auth";

    @BeforeEach
    void setUp() {
        testUser = UserDTO.builder()
                .userId(1L)
                .username("testUser")
                .email("test@example.com")
                .build();
    }

    // ==================== getUserById 方法测试 ====================

    @Test
    void testGetUserById_FromCache_Success() {
        // Given
        Long userId = 1L;
        String cacheKey = CACHE_PREFIX + userId;

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(cacheKey)).thenReturn(testUser);

        // When
        UserDTO result = userService.getUserById(userId);

        // Then
        assertNotNull(result);
        assertEquals(testUser.getUserId(), result.getUserId());
        assertEquals(testUser.getUsername(), result.getUsername());
        assertEquals(testUser.getEmail(), result.getEmail());

        verify(valueOperations, times(1)).get(cacheKey);
        verify(restTemplate, never()).exchange(anyString(), any(HttpMethod.class), any(), any(Class.class));
    }

    @Test
    void testGetUserById_FromCache_MapConversion_Success() {
        // Given
        Long userId = 1L;
        String cacheKey = CACHE_PREFIX + userId;

        Map<String, Object> cachedMap = new LinkedHashMap<>();
        cachedMap.put("userId", 1L);
        cachedMap.put("username", "testUser");
        cachedMap.put("email", "test@example.com");

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(cacheKey)).thenReturn(cachedMap);

        // When
        UserDTO result = userService.getUserById(userId);

        // Then
        assertNotNull(result);
        assertEquals(1L, result.getUserId());
        assertEquals("testUser", result.getUsername());
        assertEquals("test@example.com", result.getEmail());

        verify(valueOperations, times(1)).get(cacheKey);
        verify(restTemplate, never()).exchange(anyString(), any(HttpMethod.class), any(), any(Class.class));
    }

    @Test
    void testGetUserById_CacheMiss_CallAuthService_Success() {
        // Given
        Long userId = 1L;
        String cacheKey = CACHE_PREFIX + userId;
        String url = AUTH_SERVICE_URL + "/api/users/" + userId;

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(cacheKey)).thenReturn(null);

        ResponseEntity<UserDTO> responseEntity = ResponseEntity.ok(testUser);
        when(restTemplate.exchange(
                eq(url),
                eq(HttpMethod.GET),
                isNull(),
                eq(UserDTO.class)
        )).thenReturn(responseEntity);

        // When
        UserDTO result = userService.getUserById(userId);

        // Then
        assertNotNull(result);
        assertEquals(testUser.getUserId(), result.getUserId());
        assertEquals(testUser.getUsername(), result.getUsername());
        assertEquals(testUser.getEmail(), result.getEmail());

        verify(valueOperations, times(1)).get(cacheKey);
        verify(restTemplate, times(1)).exchange(eq(url), eq(HttpMethod.GET), isNull(), eq(UserDTO.class));
        verify(valueOperations, times(1)).set(eq(cacheKey), eq(testUser), eq(CACHE_EXPIRE_HOURS), eq(TimeUnit.HOURS));
    }

    @Test
    void testGetUserById_NullUserId_ReturnNull() {
        // When
        UserDTO result = userService.getUserById(null);

        // Then
        assertNull(result);
        verify(valueOperations, never()).get(anyString());
        verify(restTemplate, never()).exchange(anyString(), any(HttpMethod.class), any(), any(Class.class));
    }

    @Test
    void testGetUserById_AuthServiceFailed_ReturnDefaultUser() {
        // Given
        Long userId = 1L;
        String cacheKey = CACHE_PREFIX + userId;
        String url = AUTH_SERVICE_URL + "/api/users/" + userId;

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(cacheKey)).thenReturn(null);
        when(restTemplate.exchange(
                eq(url),
                eq(HttpMethod.GET),
                isNull(),
                eq(UserDTO.class)
        )).thenThrow(new RuntimeException("Auth服务异常"));

        // When
        UserDTO result = userService.getUserById(userId);

        // Then
        assertNotNull(result);
        assertEquals(userId, result.getUserId());
        assertEquals("未知用户", result.getUsername());
        assertEquals("", result.getEmail());

        verify(valueOperations, times(1)).get(cacheKey);
        verify(restTemplate, times(1)).exchange(eq(url), eq(HttpMethod.GET), isNull(), eq(UserDTO.class));
    }

    @Test
    void testGetUserById_CacheCorrupted_DeleteAndReload() {
        // Given
        Long userId = 1L;
        String cacheKey = CACHE_PREFIX + userId;
        String url = AUTH_SERVICE_URL + "/api/users/" + userId;

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(cacheKey)).thenReturn("invalid data");

        ResponseEntity<UserDTO> responseEntity = ResponseEntity.ok(testUser);
        when(restTemplate.exchange(
                eq(url),
                eq(HttpMethod.GET),
                isNull(),
                eq(UserDTO.class)
        )).thenReturn(responseEntity);

        // When
        UserDTO result = userService.getUserById(userId);

        // Then
        assertNotNull(result);
        assertEquals(testUser.getUserId(), result.getUserId());

        verify(valueOperations, times(1)).get(cacheKey);
        verify(restTemplate, times(1)).exchange(eq(url), eq(HttpMethod.GET), isNull(), eq(UserDTO.class));
    }

    // ==================== getUsersByIds 方法测试 ====================

    @Test
    void testGetUsersByIds_Success() {
        // Given
        List<Long> userIds = Arrays.asList(1L, 2L, 3L);
        List<UserDTO> expectedUsers = Arrays.asList(
                UserDTO.builder().userId(1L).username("user1").email("user1@example.com").build(),
                UserDTO.builder().userId(2L).username("user2").email("user2@example.com").build(),
                UserDTO.builder().userId(3L).username("user3").email("user3@example.com").build()
        );

        String url = AUTH_SERVICE_URL + "/api/users/batch";
        ResponseEntity<List<UserDTO>> responseEntity = ResponseEntity.ok(expectedUsers);

        when(restTemplate.exchange(
                eq(url),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class)
        )).thenReturn(responseEntity);

        // When
        List<UserDTO> result = userService.getUsersByIds(userIds);

        // Then
        assertNotNull(result);
        assertEquals(3, result.size());
        assertEquals("user1", result.get(0).getUsername());
        assertEquals("user2", result.get(1).getUsername());
        assertEquals("user3", result.get(2).getUsername());

        verify(restTemplate, times(1)).exchange(
                eq(url),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class)
        );
    }

    @Test
    void testGetUsersByIds_EmptyList_ReturnEmpty() {
        // When
        List<UserDTO> result = userService.getUsersByIds(new ArrayList<>());

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(restTemplate, never()).exchange(anyString(), any(HttpMethod.class), any(), any(ParameterizedTypeReference.class));
    }

    @Test
    void testGetUsersByIds_NullList_ReturnEmpty() {
        // When
        List<UserDTO> result = userService.getUsersByIds(null);

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(restTemplate, never()).exchange(anyString(), any(HttpMethod.class), any(), any(ParameterizedTypeReference.class));
    }

    @Test
    void testGetUsersByIds_ServiceFailed_ReturnEmpty() {
        // Given
        List<Long> userIds = Arrays.asList(1L, 2L);
        String url = AUTH_SERVICE_URL + "/api/users/batch";

        when(restTemplate.exchange(
                eq(url),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class)
        )).thenThrow(new RuntimeException("服务异常"));

        // When
        List<UserDTO> result = userService.getUsersByIds(userIds);

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());

        verify(restTemplate, times(1)).exchange(
                eq(url),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class)
        );
    }

    // ==================== searchUsers 方法测试 ====================

    @Test
    void testSearchUsers_Success() {
        // Given
        String keyword = "test";
        List<UserDTO> expectedUsers = Arrays.asList(
                UserDTO.builder().userId(1L).username("testUser1").email("test1@example.com").build(),
                UserDTO.builder().userId(2L).username("testUser2").email("test2@example.com").build()
        );

        String url = AUTH_SERVICE_URL + "/api/users/search?keyword=" + keyword;
        ResponseEntity<List<UserDTO>> responseEntity = ResponseEntity.ok(expectedUsers);

        when(restTemplate.exchange(
                eq(url),
                eq(HttpMethod.GET),
                isNull(),
                any(ParameterizedTypeReference.class)
        )).thenReturn(responseEntity);

        // When
        List<UserDTO> result = userService.searchUsers(keyword);

        // Then
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("testUser1", result.get(0).getUsername());
        assertEquals("testUser2", result.get(1).getUsername());

        verify(restTemplate, times(1)).exchange(
                eq(url),
                eq(HttpMethod.GET),
                isNull(),
                any(ParameterizedTypeReference.class)
        );
    }

    @Test
    void testSearchUsers_ServiceFailed_ReturnEmpty() {
        // Given
        String keyword = "test";
        String url = AUTH_SERVICE_URL + "/api/users/search?keyword=" + keyword;

        when(restTemplate.exchange(
                eq(url),
                eq(HttpMethod.GET),
                isNull(),
                any(ParameterizedTypeReference.class)
        )).thenThrow(new RuntimeException("搜索服务异常"));

        // When
        List<UserDTO> result = userService.searchUsers(keyword);

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());

        verify(restTemplate, times(1)).exchange(
                eq(url),
                eq(HttpMethod.GET),
                isNull(),
                any(ParameterizedTypeReference.class)
        );
    }

    @Test
    void testSearchUsers_EmptyResult_ReturnEmpty() {
        // Given
        String keyword = "nonexistent";
        String url = AUTH_SERVICE_URL + "/api/users/search?keyword=" + keyword;
        ResponseEntity<List<UserDTO>> responseEntity = ResponseEntity.ok(new ArrayList<>());

        when(restTemplate.exchange(
                eq(url),
                eq(HttpMethod.GET),
                isNull(),
                any(ParameterizedTypeReference.class)
        )).thenReturn(responseEntity);

        // When
        List<UserDTO> result = userService.searchUsers(keyword);

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());

        verify(restTemplate, times(1)).exchange(
                eq(url),
                eq(HttpMethod.GET),
                isNull(),
                any(ParameterizedTypeReference.class)
        );
    }
}
