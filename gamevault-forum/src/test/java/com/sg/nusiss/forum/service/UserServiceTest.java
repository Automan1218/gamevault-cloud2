package com.sg.nusiss.forum.service;

import com.sg.nusiss.common.dto.UserDTO;
import com.sg.nusiss.forum.service.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * UserService 单元测试
 *
 * 测试要点:
 * 1. 测试成功获取用户信息
 * 2. 测试网络异常处理
 * 3. 测试HTTP错误处理
 * 4. 测试空值处理
 * 5. 测试批量操作
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserService 单元测试")
class UserServiceTest {

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private UserService userService;

    private final String authServiceUrl = "http://gamevault-auth";

    @BeforeEach
    void setUp() {
        // 设置私有字段的值
        ReflectionTestUtils.setField(userService, "authServiceUrl", authServiceUrl);
    }

    // ========================================
    // getUserById 测试
    // ========================================

    @Test
    @DisplayName("getUserById - 成功获取用户信息")
    void testGetUserById_Success() {
        // Given
        Long userId = 1L;
        UserDTO expectedUser = createTestUser(userId, "testuser", "test@example.com");

        when(restTemplate.exchange(
                eq(authServiceUrl + "/api/users/" + userId),
                eq(HttpMethod.GET),
                isNull(),
                any(ParameterizedTypeReference.class)
        )).thenReturn(ResponseEntity.ok(expectedUser));

        // When
        UserDTO result = userService.getUserById(userId);

        // Then
        assertNotNull(result);
        assertEquals(userId, result.getUserId());
        assertEquals("testuser", result.getUsername());
        assertEquals("test@example.com", result.getEmail());

        verify(restTemplate, times(1)).exchange(
                anyString(),
                eq(HttpMethod.GET),
                isNull(),
                any(ParameterizedTypeReference.class)
        );
    }

    @Test
    @DisplayName("getUserById - 用户ID为null")
    void testGetUserById_NullUserId() {
        // When
        UserDTO result = userService.getUserById(null);

        // Then
        assertNull(result);
        verify(restTemplate, never()).exchange(
                anyString(),
                any(HttpMethod.class),
                any(),
                any(ParameterizedTypeReference.class)
        );
    }

    @Test
    @DisplayName("getUserById - 响应体为空")
    void testGetUserById_EmptyResponse() {
        // Given
        Long userId = 1L;
        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.GET),
                isNull(),
                any(ParameterizedTypeReference.class)
        )).thenReturn(ResponseEntity.ok(null));

        // When
        UserDTO result = userService.getUserById(userId);

        // Then
        assertNull(result);
    }

    @Test
    @DisplayName("getUserById - 用户不存在(404)")
    void testGetUserById_NotFound() {
        // Given
        Long userId = 999L;
        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.GET),
                isNull(),
                any(ParameterizedTypeReference.class)
        )).thenThrow(new HttpClientErrorException(HttpStatus.NOT_FOUND));

        // When
        UserDTO result = userService.getUserById(userId);

        // Then
        assertNull(result);
    }

    @Test
    @DisplayName("getUserById - 网络连接失败")
    void testGetUserById_NetworkError() {
        // Given
        Long userId = 1L;
        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.GET),
                isNull(),
                any(ParameterizedTypeReference.class)
        )).thenThrow(new ResourceAccessException("Connection refused"));

        // When
        UserDTO result = userService.getUserById(userId);

        // Then
        assertNull(result);
    }

    @Test
    @DisplayName("getUserById - HTTP客户端错误(400)")
    void testGetUserById_ClientError() {
        // Given
        Long userId = 1L;
        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.GET),
                isNull(),
                any(ParameterizedTypeReference.class)
        )).thenThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST));

        // When
        UserDTO result = userService.getUserById(userId);

        // Then
        assertNull(result);
    }

    @Test
    @DisplayName("getUserById - 通用异常")
    void testGetUserById_GeneralException() {
        // Given
        Long userId = 1L;
        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.GET),
                isNull(),
                any(ParameterizedTypeReference.class)
        )).thenThrow(new RuntimeException("Unexpected error"));

        // When
        UserDTO result = userService.getUserById(userId);

        // Then
        assertNull(result);
    }

    // ========================================
    // getUsersByIds 测试
    // ========================================

    @Test
    @DisplayName("getUsersByIds - 成功批量获取用户")
    void testGetUsersByIds_Success() {
        // Given
        List<Long> userIds = Arrays.asList(1L, 2L, 3L);
        List<UserDTO> expectedUsers = Arrays.asList(
                createTestUser(1L, "user1", "user1@example.com"),
                createTestUser(2L, "user2", "user2@example.com"),
                createTestUser(3L, "user3", "user3@example.com")
        );

        when(restTemplate.exchange(
                eq(authServiceUrl + "/api/users/batch"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class)
        )).thenReturn(ResponseEntity.ok(expectedUsers));

        // When
        List<UserDTO> result = userService.getUsersByIds(userIds);

        // Then
        assertNotNull(result);
        assertEquals(3, result.size());
        assertEquals("user1", result.get(0).getUsername());
        assertEquals("user2", result.get(1).getUsername());
        assertEquals("user3", result.get(2).getUsername());

        verify(restTemplate, times(1)).exchange(
                anyString(),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class)
        );
    }

    @Test
    @DisplayName("getUsersByIds - 空列表")
    void testGetUsersByIds_EmptyList() {
        // Given
        List<Long> userIds = Arrays.asList();

        // When
        List<UserDTO> result = userService.getUsersByIds(userIds);

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(restTemplate, never()).exchange(
                anyString(),
                any(HttpMethod.class),
                any(),
                any(ParameterizedTypeReference.class)
        );
    }

    @Test
    @DisplayName("getUsersByIds - null参数")
    void testGetUsersByIds_NullParameter() {
        // When
        List<UserDTO> result = userService.getUsersByIds(null);

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(restTemplate, never()).exchange(
                anyString(),
                any(HttpMethod.class),
                any(),
                any(ParameterizedTypeReference.class)
        );
    }

    @Test
    @DisplayName("getUsersByIds - 请求失败")
    void testGetUsersByIds_RequestFailed() {
        // Given
        List<Long> userIds = Arrays.asList(1L, 2L);
        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class)
        )).thenThrow(new RuntimeException("Request failed"));

        // When
        List<UserDTO> result = userService.getUsersByIds(userIds);

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("getUsersByIds - 响应体为空")
    void testGetUsersByIds_EmptyResponse() {
        // Given
        List<Long> userIds = Arrays.asList(1L, 2L);
        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class)
        )).thenReturn(ResponseEntity.ok(null));

        // When
        List<UserDTO> result = userService.getUsersByIds(userIds);

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // ========================================
    // searchUsers 测试
    // ========================================

    @Test
    @DisplayName("searchUsers - 成功搜索用户")
    void testSearchUsers_Success() {
        // Given
        String keyword = "test";
        List<UserDTO> expectedUsers = Arrays.asList(
                createTestUser(1L, "testuser1", "test1@example.com"),
                createTestUser(2L, "testuser2", "test2@example.com")
        );

        when(restTemplate.exchange(
                eq(authServiceUrl + "/api/users/search?keyword=" + keyword),
                eq(HttpMethod.GET),
                isNull(),
                any(ParameterizedTypeReference.class)
        )).thenReturn(ResponseEntity.ok(expectedUsers));

        // When
        List<UserDTO> result = userService.searchUsers(keyword);

        // Then
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("testuser1", result.get(0).getUsername());
        assertEquals("testuser2", result.get(1).getUsername());

        verify(restTemplate, times(1)).exchange(
                anyString(),
                eq(HttpMethod.GET),
                isNull(),
                any(ParameterizedTypeReference.class)
        );
    }

    @Test
    @DisplayName("searchUsers - 无搜索结果")
    void testSearchUsers_NoResults() {
        // Given
        String keyword = "nonexistent";
        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.GET),
                isNull(),
                any(ParameterizedTypeReference.class)
        )).thenReturn(ResponseEntity.ok(Arrays.asList()));

        // When
        List<UserDTO> result = userService.searchUsers(keyword);

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("searchUsers - 搜索失败")
    void testSearchUsers_SearchFailed() {
        // Given
        String keyword = "test";
        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.GET),
                isNull(),
                any(ParameterizedTypeReference.class)
        )).thenThrow(new RuntimeException("Search failed"));

        // When
        List<UserDTO> result = userService.searchUsers(keyword);

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("searchUsers - 响应体为空")
    void testSearchUsers_EmptyResponse() {
        // Given
        String keyword = "test";
        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.GET),
                isNull(),
                any(ParameterizedTypeReference.class)
        )).thenReturn(ResponseEntity.ok(null));

        // When
        List<UserDTO> result = userService.searchUsers(keyword);

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // ========================================
    // 辅助方法
    // ========================================

    /**
     * 创建测试用户
     */
    private UserDTO createTestUser(Long userId, String username, String email) {
        UserDTO user = new UserDTO();
        user.setUserId(userId);
        user.setUsername(username);
        user.setEmail(email);
        user.setAvatarUrl("http://example.com/avatar/" + userId + ".jpg");
        return user;
    }
}