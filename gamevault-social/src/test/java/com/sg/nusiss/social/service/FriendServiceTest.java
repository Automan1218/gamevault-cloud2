package com.sg.nusiss.social.service;

import com.sg.nusiss.common.dto.UserDTO;
import com.sg.nusiss.common.exception.BusinessException;
import com.sg.nusiss.social.dto.friend.response.FriendRequestResponse;
import com.sg.nusiss.social.dto.friend.response.FriendResponse;
import com.sg.nusiss.social.dto.friend.response.UserSearchResponse;
import com.sg.nusiss.social.entity.friend.FriendRequest;
import com.sg.nusiss.social.entity.friend.Friendship;
import com.sg.nusiss.social.repository.friend.FriendRequestRepository;
import com.sg.nusiss.social.repository.friend.FriendshipRepository;
import com.sg.nusiss.social.service.friend.FriendService;
import com.sg.nusiss.social.service.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FriendServiceTest {

    @Mock
    private UserService userService;

    @Mock
    private FriendRequestRepository friendRequestRepository;

    @Mock
    private FriendshipRepository friendshipRepository;

    @InjectMocks
    private FriendService friendService;

    private UserDTO testUser1;
    private UserDTO testUser2;
    private UserDTO testUser3;
    private FriendRequest testRequest;
    private Friendship testFriendship;

    @BeforeEach
    void setUp() {
        testUser1 = UserDTO.builder()
                .userId(1L)
                .username("user1")
                .email("user1@example.com")
                .build();

        testUser2 = UserDTO.builder()
                .userId(2L)
                .username("user2")
                .email("user2@example.com")
                .build();

        testUser3 = UserDTO.builder()
                .userId(3L)
                .username("user3")
                .email("user3@example.com")
                .build();

        testRequest = new FriendRequest();
        testRequest.setId(1L);
        testRequest.setFromUserId(1L);
        testRequest.setToUserId(2L);
        testRequest.setMessage("Hello");
        testRequest.setStatus("pending");
        testRequest.setCreatedAt(LocalDateTime.now());

        testFriendship = new Friendship();
        testFriendship.setId(1L);
        testFriendship.setUserId(1L);
        testFriendship.setFriendId(2L);
        testFriendship.setIsActive(true);
        testFriendship.setCreatedAt(LocalDateTime.now());
    }

    // ==================== searchUsers 方法测试 ====================

    @Test
    void testSearchUsers_Success() {
        // Given
        String keyword = "user";
        Long currentUserId = 1L;

        when(userService.searchUsers("user")).thenReturn(Arrays.asList(testUser1, testUser2, testUser3));
        // 移除这行: when(friendshipRepository.findByUserIdAndFriendIdAndIsActive(1L, 1L, true)).thenReturn(Optional.empty());
        when(friendshipRepository.findByUserIdAndFriendIdAndIsActive(1L, 2L, true)).thenReturn(Optional.of(testFriendship));
        when(friendshipRepository.findByUserIdAndFriendIdAndIsActive(1L, 3L, true)).thenReturn(Optional.empty());
        when(friendRequestRepository.findExistingRequest(1L, 2L, "pending")).thenReturn(Optional.empty());
        when(friendRequestRepository.findExistingRequest(1L, 3L, "pending")).thenReturn(Optional.of(testRequest));

        // When
        List<UserSearchResponse> result = friendService.searchUsers(keyword, currentUserId);

        // Then
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals(2L, result.get(0).getUserId());
        assertTrue(result.get(0).getIsFriend());
        assertEquals(3L, result.get(1).getUserId());
        assertFalse(result.get(1).getIsFriend());
        assertTrue(result.get(1).getHasPending());

        verify(userService, times(1)).searchUsers("user");
    }

    @Test
    void testSearchUsers_EmptyKeyword_ThrowException() {
        // When & Then
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            friendService.searchUsers("   ", 1L);
        });

        assertEquals(40000, exception.getCode());
        assertEquals("搜索关键词不能为空", exception.getMessage());

        verify(userService, never()).searchUsers(anyString());
    }

    @Test
    void testSearchUsers_NullKeyword_ThrowException() {
        // When & Then
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            friendService.searchUsers(null, 1L);
        });

        assertEquals(40000, exception.getCode());
        verify(userService, never()).searchUsers(anyString());
    }

    // ==================== sendFriendRequest 方法测试 ====================

    @Test
    void testSendFriendRequest_Success() {
        // Given
        Long fromUserId = 1L;
        Long toUserId = 2L;
        String message = "Hello";

        when(userService.getUserById(2L)).thenReturn(testUser2);
        when(friendshipRepository.findByUserIdAndFriendIdAndIsActive(1L, 2L, true)).thenReturn(Optional.empty());
        when(friendRequestRepository.findExistingRequest(1L, 2L, "pending")).thenReturn(Optional.empty());
        when(friendRequestRepository.save(any(FriendRequest.class))).thenReturn(testRequest);

        // When
        friendService.sendFriendRequest(fromUserId, toUserId, message);

        // Then
        verify(userService, times(1)).getUserById(2L);
        verify(friendshipRepository, times(1)).findByUserIdAndFriendIdAndIsActive(1L, 2L, true);
        verify(friendRequestRepository, times(1)).findExistingRequest(1L, 2L, "pending");
        verify(friendRequestRepository, times(1)).save(any(FriendRequest.class));
    }

    @Test
    void testSendFriendRequest_UserNotFound_ThrowException() {
        // Given
        when(userService.getUserById(2L)).thenReturn(null);

        // When & Then
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            friendService.sendFriendRequest(1L, 2L, "Hello");
        });

        assertEquals(40400, exception.getCode());
        assertEquals("用户不存在", exception.getMessage());

        verify(friendRequestRepository, never()).save(any(FriendRequest.class));
    }

    @Test
    void testSendFriendRequest_AddSelf_ThrowException() {
        // Given
        when(userService.getUserById(1L)).thenReturn(testUser1);

        // When & Then
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            friendService.sendFriendRequest(1L, 1L, "Hello");
        });

        assertEquals(40000, exception.getCode());
        assertEquals("不能添加自己为好友", exception.getMessage());

        verify(friendRequestRepository, never()).save(any(FriendRequest.class));
    }

    @Test
    void testSendFriendRequest_AlreadyFriend_ThrowException() {
        // Given
        when(userService.getUserById(2L)).thenReturn(testUser2);
        when(friendshipRepository.findByUserIdAndFriendIdAndIsActive(1L, 2L, true))
                .thenReturn(Optional.of(testFriendship));

        // When & Then
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            friendService.sendFriendRequest(1L, 2L, "Hello");
        });

        assertEquals(50001, exception.getCode());
        assertEquals("已经是好友了", exception.getMessage());

        verify(friendRequestRepository, never()).save(any(FriendRequest.class));
    }

    @Test
    void testSendFriendRequest_PendingRequest_ThrowException() {
        // Given
        when(userService.getUserById(2L)).thenReturn(testUser2);
        when(friendshipRepository.findByUserIdAndFriendIdAndIsActive(1L, 2L, true)).thenReturn(Optional.empty());
        when(friendRequestRepository.findExistingRequest(1L, 2L, "pending"))
                .thenReturn(Optional.of(testRequest));

        // When & Then
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            friendService.sendFriendRequest(1L, 2L, "Hello");
        });

        assertEquals(50001, exception.getCode());
        assertEquals("已有待处理的好友请求", exception.getMessage());

        verify(friendRequestRepository, never()).save(any(FriendRequest.class));
    }

    // ==================== getReceivedRequests 方法测试 ====================

    @Test
    void testGetReceivedRequests_Success() {
        // Given
        Long userId = 2L;

        when(friendRequestRepository.findByToUserIdAndStatus(2L, "pending"))
                .thenReturn(Arrays.asList(testRequest));
        when(userService.getUserById(1L)).thenReturn(testUser1);
        when(userService.getUserById(2L)).thenReturn(testUser2);

        // When
        List<FriendRequestResponse> result = friendService.getReceivedRequests(userId);

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).getId());
        assertEquals(1L, result.get(0).getFromUserId());
        assertEquals("user1", result.get(0).getFromUsername());

        verify(friendRequestRepository, times(1)).findByToUserIdAndStatus(2L, "pending");
    }

    @Test
    void testGetReceivedRequests_EmptyResult() {
        // Given
        when(friendRequestRepository.findByToUserIdAndStatus(2L, "pending")).thenReturn(Arrays.asList());

        // When
        List<FriendRequestResponse> result = friendService.getReceivedRequests(2L);

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // ==================== getSentRequests 方法测试 ====================

    @Test
    void testGetSentRequests_Success() {
        // Given
        Long userId = 1L;

        when(friendRequestRepository.findByFromUserIdAndStatus(1L, "pending"))
                .thenReturn(Arrays.asList(testRequest));
        when(userService.getUserById(1L)).thenReturn(testUser1);
        when(userService.getUserById(2L)).thenReturn(testUser2);

        // When
        List<FriendRequestResponse> result = friendService.getSentRequests(userId);

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).getId());
        assertEquals(2L, result.get(0).getToUserId());
        assertEquals("user2", result.get(0).getToUsername());

        verify(friendRequestRepository, times(1)).findByFromUserIdAndStatus(1L, "pending");
    }

    // ==================== handleFriendRequest 方法测试 ====================

    @Test
    void testHandleFriendRequest_Accept_Success() {
        // Given
        Long requestId = 1L;
        Long currentUserId = 2L;

        when(friendRequestRepository.findById(1L)).thenReturn(Optional.of(testRequest));
        when(friendRequestRepository.save(any(FriendRequest.class))).thenReturn(testRequest);
        when(friendshipRepository.save(any(Friendship.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        friendService.handleFriendRequest(requestId, true, currentUserId);

        // Then
        verify(friendRequestRepository, times(1)).findById(1L);
        verify(friendRequestRepository, times(1)).save(any(FriendRequest.class));
        verify(friendshipRepository, times(2)).save(any(Friendship.class));
    }

    @Test
    void testHandleFriendRequest_Reject_Success() {
        // Given
        Long requestId = 1L;
        Long currentUserId = 2L;

        when(friendRequestRepository.findById(1L)).thenReturn(Optional.of(testRequest));
        when(friendRequestRepository.save(any(FriendRequest.class))).thenReturn(testRequest);

        // When
        friendService.handleFriendRequest(requestId, false, currentUserId);

        // Then
        verify(friendRequestRepository, times(1)).findById(1L);
        verify(friendRequestRepository, times(1)).save(any(FriendRequest.class));
        verify(friendshipRepository, never()).save(any(Friendship.class));
    }

    @Test
    void testHandleFriendRequest_NotFound_ThrowException() {
        // Given
        when(friendRequestRepository.findById(1L)).thenReturn(Optional.empty());

        // When & Then
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            friendService.handleFriendRequest(1L, true, 2L);
        });

        assertEquals(40400, exception.getCode());
        assertEquals("好友请求不存在", exception.getMessage());

        verify(friendRequestRepository, never()).save(any(FriendRequest.class));
    }

    @Test
    void testHandleFriendRequest_NoPermission_ThrowException() {
        // Given
        when(friendRequestRepository.findById(1L)).thenReturn(Optional.of(testRequest));

        // When & Then
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            friendService.handleFriendRequest(1L, true, 3L);
        });

        assertEquals(40101, exception.getCode());
        assertEquals("无权处理此请求", exception.getMessage());

        verify(friendRequestRepository, never()).save(any(FriendRequest.class));
    }

    @Test
    void testHandleFriendRequest_AlreadyHandled_ThrowException() {
        // Given
        testRequest.setStatus("accepted");
        when(friendRequestRepository.findById(1L)).thenReturn(Optional.of(testRequest));

        // When & Then
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            friendService.handleFriendRequest(1L, true, 2L);
        });

        assertEquals(50001, exception.getCode());
        assertEquals("请求已处理", exception.getMessage());

        verify(friendRequestRepository, never()).save(any(FriendRequest.class));
    }

    // ==================== getFriends 方法测试 ====================

    @Test
    void testGetFriends_Success() {
        // Given
        Long userId = 1L;

        Friendship friendship2 = new Friendship();
        friendship2.setId(2L);
        friendship2.setUserId(1L);
        friendship2.setFriendId(3L);
        friendship2.setIsActive(true);
        friendship2.setCreatedAt(LocalDateTime.now());

        when(friendshipRepository.findByUserIdAndIsActive(1L, true))
                .thenReturn(Arrays.asList(testFriendship, friendship2));
        when(userService.getUserById(2L)).thenReturn(testUser2);
        when(userService.getUserById(3L)).thenReturn(testUser3);

        // When
        List<FriendResponse> result = friendService.getFriends(userId);

        // Then
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals(2L, result.get(0).getUserId());
        assertEquals("user2", result.get(0).getUsername());
        assertEquals(3L, result.get(1).getUserId());
        assertEquals("user3", result.get(1).getUsername());

        verify(friendshipRepository, times(1)).findByUserIdAndIsActive(1L, true);
    }

    @Test
    void testGetFriends_EmptyResult() {
        // Given
        when(friendshipRepository.findByUserIdAndIsActive(1L, true)).thenReturn(Arrays.asList());

        // When
        List<FriendResponse> result = friendService.getFriends(1L);

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // ==================== deleteFriend 方法测试 ====================

    @Test
    void testDeleteFriend_Success() {
        // Given
        Long userId = 1L;
        Long friendId = 2L;

        Friendship friendship1 = new Friendship();
        friendship1.setUserId(1L);
        friendship1.setFriendId(2L);
        friendship1.setIsActive(true);

        Friendship friendship2 = new Friendship();
        friendship2.setUserId(2L);
        friendship2.setFriendId(1L);
        friendship2.setIsActive(true);

        when(friendshipRepository.findByUserIdAndFriendIdAndIsActive(1L, 2L, true))
                .thenReturn(Optional.of(friendship1));
        when(friendshipRepository.findByUserIdAndFriendIdAndIsActive(2L, 1L, true))
                .thenReturn(Optional.of(friendship2));
        when(friendshipRepository.save(any(Friendship.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        friendService.deleteFriend(userId, friendId);

        // Then
        assertFalse(friendship1.getIsActive());
        assertNotNull(friendship1.getDeletedAt());
        assertFalse(friendship2.getIsActive());
        assertNotNull(friendship2.getDeletedAt());

        verify(friendshipRepository, times(1)).findByUserIdAndFriendIdAndIsActive(1L, 2L, true);
        verify(friendshipRepository, times(1)).findByUserIdAndFriendIdAndIsActive(2L, 1L, true);
        verify(friendshipRepository, times(2)).save(any(Friendship.class));
    }

    @Test
    void testDeleteFriend_NotFound_ThrowException() {
        // Given
        when(friendshipRepository.findByUserIdAndFriendIdAndIsActive(1L, 2L, true))
                .thenReturn(Optional.empty());

        // When & Then
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            friendService.deleteFriend(1L, 2L);
        });

        assertEquals(40400, exception.getCode());
        assertEquals("好友关系不存在", exception.getMessage());

        verify(friendshipRepository, never()).save(any(Friendship.class));
    }
}