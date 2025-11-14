package com.sg.nusiss.social.service;

import com.sg.nusiss.common.dto.UserDTO;
import com.sg.nusiss.common.exception.BusinessException;
import com.sg.nusiss.social.dto.conversation.response.ConversationListResponse;
import com.sg.nusiss.social.dto.conversation.response.MemberResponse;
import com.sg.nusiss.social.entity.conversation.Conversation;
import com.sg.nusiss.social.entity.conversation.Member;
import com.sg.nusiss.social.entity.friend.Friendship;
import com.sg.nusiss.social.repository.conversation.ConversationRepository;
import com.sg.nusiss.social.repository.conversation.MemberRepository;
import com.sg.nusiss.social.repository.friend.FriendshipRepository;
import com.sg.nusiss.social.service.conversation.ConversationService;
import com.sg.nusiss.social.service.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConversationServiceTest {

    @Mock
    private ConversationRepository conversationRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private UserService userService;

    @Mock
    private FriendshipRepository friendshipRepository;

    @InjectMocks
    private ConversationService conversationService;

    private UserDTO testOwner;
    private UserDTO testUser1;
    private UserDTO testUser2;
    private Conversation testConversation;
    private Member testMember;
    private Friendship testFriendship;

    @BeforeEach
    void setUp() {
        testOwner = UserDTO.builder()
                .userId(1L)
                .username("owner")
                .email("owner@example.com")
                .build();

        testUser1 = UserDTO.builder()
                .userId(2L)
                .username("user1")
                .email("user1@example.com")
                .build();

        testUser2 = UserDTO.builder()
                .userId(3L)
                .username("user2")
                .email("user2@example.com")
                .build();

        testConversation = new Conversation();
        testConversation.setId(100L);
        testConversation.setTitle("测试群聊");
        testConversation.setOwnerId(1L);
        testConversation.setStatus("active");
        testConversation.setCreatedAt(LocalDateTime.now());

        testMember = new Member();
        testMember.setId(1L);
        testMember.setConversation(testConversation);
        testMember.setUserId(1L);
        testMember.setRole("owner");
        testMember.setIsActive(true);
        testMember.setJoinedAt(LocalDateTime.now());

        testFriendship = new Friendship();
        testFriendship.setUserId(1L);
        testFriendship.setFriendId(2L);
        testFriendship.setIsActive(true);
    }

    // ==================== createConversation 方法测试 ====================

    @Test
    void testCreateConversation_Success() {
        // Given
        String title = "测试群聊";
        Long ownerId = 1L;

        when(userService.getUserById(1L)).thenReturn(testOwner);
        when(conversationRepository.save(any(Conversation.class))).thenReturn(testConversation);
        when(memberRepository.save(any(Member.class))).thenReturn(testMember);

        // When
        Conversation result = conversationService.createConversation(title, ownerId);

        // Then
        assertNotNull(result);
        assertEquals(100L, result.getId());
        assertEquals("测试群聊", result.getTitle());
        assertEquals(1L, result.getOwnerId());

        verify(userService, times(1)).getUserById(1L);
        verify(conversationRepository, times(1)).save(any(Conversation.class));
        verify(memberRepository, times(1)).save(any(Member.class));
    }

    @Test
    void testCreateConversation_EmptyTitle_ThrowException() {
        // When & Then
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            conversationService.createConversation("   ", 1L);
        });

        assertEquals(40000, exception.getCode());
        assertEquals("群聊标题不能为空", exception.getMessage());

        verify(conversationRepository, never()).save(any(Conversation.class));
    }

    @Test
    void testCreateConversation_NullOwnerId_ThrowException() {
        // When & Then
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            conversationService.createConversation("测试群聊", null);
        });

        assertEquals(40000, exception.getCode());
        assertEquals("ownerId 不能为空", exception.getMessage());

        verify(conversationRepository, never()).save(any(Conversation.class));
    }

    @Test
    void testCreateConversation_OwnerNotFound_ThrowException() {
        // Given
        when(userService.getUserById(1L)).thenReturn(null);

        // When & Then
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            conversationService.createConversation("测试群聊", 1L);
        });

        assertEquals(40400, exception.getCode());
        assertEquals("用户不存在", exception.getMessage());

        verify(conversationRepository, never()).save(any(Conversation.class));
    }

    // ==================== getUserConversations 方法测试 ====================

    @Test
    void testGetUserConversations_Success() {
        // Given
        Long userId = 1L;

        Member member1 = new Member();
        member1.setConversation(testConversation);
        member1.setUserId(userId);

        Conversation conv2 = new Conversation();
        conv2.setId(101L);
        conv2.setTitle("另一个群聊");
        conv2.setOwnerId(2L);
        conv2.setStatus("active");
        conv2.setCreatedAt(LocalDateTime.now());

        Member member2 = new Member();
        member2.setConversation(conv2);
        member2.setUserId(userId);

        when(memberRepository.findByUserId(userId)).thenReturn(Arrays.asList(member1, member2));

        // When
        List<ConversationListResponse> result = conversationService.getUserConversations(userId);

        // Then
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals(100L, result.get(0).getId());
        assertEquals("测试群聊", result.get(0).getTitle());
        assertEquals(101L, result.get(1).getId());
        assertEquals("另一个群聊", result.get(1).getTitle());

        verify(memberRepository, times(1)).findByUserId(userId);
    }

    @Test
    void testGetUserConversations_EmptyResult() {
        // Given
        when(memberRepository.findByUserId(1L)).thenReturn(Collections.emptyList());

        // When
        List<ConversationListResponse> result = conversationService.getUserConversations(1L);

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // ==================== dissolveConversation 方法测试 ====================

    @Test
    void testDissolveConversation_Success() {
        // Given
        Long conversationId = 100L;
        Long currentUserId = 1L;

        Member activeMember1 = new Member();
        activeMember1.setUserId(1L);
        activeMember1.setIsActive(true);

        Member activeMember2 = new Member();
        activeMember2.setUserId(2L);
        activeMember2.setIsActive(true);

        when(conversationRepository.findById(100L)).thenReturn(Optional.of(testConversation));
        when(memberRepository.findByConversationIdAndIsActive(100L, true))
                .thenReturn(Arrays.asList(activeMember1, activeMember2));
        when(conversationRepository.save(any(Conversation.class))).thenReturn(testConversation);
        when(memberRepository.saveAll(anyList())).thenReturn(Arrays.asList(activeMember1, activeMember2));

        // When
        conversationService.dissolveConversation(conversationId, currentUserId);

        // Then
        verify(conversationRepository, times(1)).findById(100L);
        verify(conversationRepository, times(1)).save(any(Conversation.class));
        verify(memberRepository, times(1)).saveAll(anyList());
    }

    @Test
    void testDissolveConversation_NullConversationId_ThrowException() {
        // When & Then
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            conversationService.dissolveConversation(null, 1L);
        });

        assertEquals(40000, exception.getCode());
        assertEquals("群聊ID不能为空", exception.getMessage());
    }

    @Test
    void testDissolveConversation_NullUserId_ThrowException() {
        // When & Then
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            conversationService.dissolveConversation(100L, null);
        });

        assertEquals(40100, exception.getCode());
        assertEquals("用户未登录", exception.getMessage());
    }

    @Test
    void testDissolveConversation_ConversationNotFound_ThrowException() {
        // Given
        when(conversationRepository.findById(100L)).thenReturn(Optional.empty());

        // When & Then
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            conversationService.dissolveConversation(100L, 1L);
        });

        assertEquals(40400, exception.getCode());
        assertEquals("群聊不存在", exception.getMessage());
    }

    @Test
    void testDissolveConversation_AlreadyDissolved_ThrowException() {
        // Given
        testConversation.setStatus("dissolved");
        when(conversationRepository.findById(100L)).thenReturn(Optional.of(testConversation));

        // When & Then
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            conversationService.dissolveConversation(100L, 1L);
        });

        assertEquals(50001, exception.getCode());
        assertEquals("群聊已被解散", exception.getMessage());
    }

    @Test
    void testDissolveConversation_NotOwner_ThrowException() {
        // Given
        when(conversationRepository.findById(100L)).thenReturn(Optional.of(testConversation));

        // When & Then
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            conversationService.dissolveConversation(100L, 2L);
        });

        assertEquals(40101, exception.getCode());
        assertEquals("只有群主可以解散群聊", exception.getMessage());
    }

    // ==================== getMembers 方法测试 ====================

    @Test
    void testGetMembers_Success() {
        // Given
        Long conversationId = 100L;
        Long currentUserId = 1L;

        Member member1 = new Member();
        member1.setUserId(1L);
        member1.setRole("owner");
        member1.setIsActive(true);
        member1.setJoinedAt(LocalDateTime.now());

        Member member2 = new Member();
        member2.setUserId(2L);
        member2.setRole("member");
        member2.setIsActive(true);
        member2.setJoinedAt(LocalDateTime.now());

        when(conversationRepository.findById(100L)).thenReturn(Optional.of(testConversation));
        when(memberRepository.existsByConversationIdAndUserIdAndIsActive(100L, 1L, true))
                .thenReturn(true);
        when(memberRepository.findByConversationIdAndIsActive(100L, true))
                .thenReturn(Arrays.asList(member1, member2));
        when(userService.getUsersByIds(Arrays.asList(1L, 2L)))
                .thenReturn(Arrays.asList(testOwner, testUser1));

        // When
        List<MemberResponse> result = conversationService.getMembers(conversationId, currentUserId);

        // Then
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals(1L, result.get(0).getUserId());
        assertEquals("owner", result.get(0).getUsername());
        assertEquals("owner", result.get(0).getRole());
        assertEquals(2L, result.get(1).getUserId());
        assertEquals("user1", result.get(1).getUsername());
        assertEquals("member", result.get(1).getRole());

        verify(conversationRepository, times(1)).findById(100L);
        verify(userService, times(1)).getUsersByIds(anyList());
    }

    @Test
    void testGetMembers_ConversationNotFound_ThrowException() {
        // Given
        when(conversationRepository.findById(100L)).thenReturn(Optional.empty());

        // When & Then
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            conversationService.getMembers(100L, 1L);
        });

        assertEquals(40400, exception.getCode());
        assertEquals("群聊不存在", exception.getMessage());
    }

    @Test
    void testGetMembers_ConversationDissolved_ThrowException() {
        // Given
        testConversation.setStatus("dissolved");
        when(conversationRepository.findById(100L)).thenReturn(Optional.of(testConversation));

        // When & Then
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            conversationService.getMembers(100L, 1L);
        });

        assertEquals(50001, exception.getCode());
        assertEquals("群聊已解散", exception.getMessage());
    }

    @Test
    void testGetMembers_NotMember_ThrowException() {
        // Given
        when(conversationRepository.findById(100L)).thenReturn(Optional.of(testConversation));
        when(memberRepository.existsByConversationIdAndUserIdAndIsActive(100L, 1L, true))
                .thenReturn(false);

        // When & Then
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            conversationService.getMembers(100L, 1L);
        });

        assertEquals(40101, exception.getCode());
        assertEquals("您不在该群聊中", exception.getMessage());
    }

    // ==================== addMembers 方法测试 ====================

    @Test
    void testAddMembers_Success() {
        // Given
        Long conversationId = 100L;
        Long currentUserId = 1L;
        List<Long> userIds = Arrays.asList(2L);

        when(conversationRepository.findById(100L)).thenReturn(Optional.of(testConversation));
        when(memberRepository.findByConversationIdAndUserIdAndIsActive(100L, 1L, true))
                .thenReturn(Optional.of(testMember));
        when(userService.getUserById(2L)).thenReturn(testUser1);
        when(friendshipRepository.findByUserIdAndFriendIdAndIsActive(1L, 2L, true))
                .thenReturn(Optional.of(testFriendship));
        when(memberRepository.findByConversationIdAndUserIdAndIsActive(100L, 2L, true))
                .thenReturn(Optional.empty());
        when(memberRepository.save(any(Member.class))).thenReturn(new Member());

        // When
        conversationService.addMembers(conversationId, userIds, currentUserId);

        // Then
        verify(conversationRepository, times(1)).findById(100L);
        verify(userService, times(1)).getUserById(2L);
        verify(friendshipRepository, times(1)).findByUserIdAndFriendIdAndIsActive(1L, 2L, true);
        verify(memberRepository, times(1)).save(any(Member.class));
    }

    @Test
    void testAddMembers_ConversationNotFound_ThrowException() {
        // Given
        when(conversationRepository.findById(100L)).thenReturn(Optional.empty());

        // When & Then
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            conversationService.addMembers(100L, Arrays.asList(2L), 1L);
        });

        assertEquals(40400, exception.getCode());
        assertEquals("群聊不存在", exception.getMessage());
    }

    @Test
    void testAddMembers_ConversationDissolved_ThrowException() {
        // Given
        testConversation.setStatus("dissolved");
        when(conversationRepository.findById(100L)).thenReturn(Optional.of(testConversation));

        // When & Then
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            conversationService.addMembers(100L, Arrays.asList(2L), 1L);
        });

        assertEquals(50001, exception.getCode());
        assertEquals("群聊已解散", exception.getMessage());
    }

    @Test
    void testAddMembers_NotMember_ThrowException() {
        // Given
        when(conversationRepository.findById(100L)).thenReturn(Optional.of(testConversation));
        when(memberRepository.findByConversationIdAndUserIdAndIsActive(100L, 1L, true))
                .thenReturn(Optional.empty());

        // When & Then
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            conversationService.addMembers(100L, Arrays.asList(2L), 1L);
        });

        assertEquals(40101, exception.getCode());
        assertEquals("您不是群成员", exception.getMessage());
    }

    @Test
    void testAddMembers_UserNotFound_ThrowException() {
        // Given
        when(conversationRepository.findById(100L)).thenReturn(Optional.of(testConversation));
        when(memberRepository.findByConversationIdAndUserIdAndIsActive(100L, 1L, true))
                .thenReturn(Optional.of(testMember));
        when(userService.getUserById(2L)).thenReturn(null);

        // When & Then
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            conversationService.addMembers(100L, Arrays.asList(2L), 1L);
        });

        assertEquals(40400, exception.getCode());
        assertEquals("用户不存在", exception.getMessage());
    }

    @Test
    void testAddMembers_NotFriend_ThrowException() {
        // Given
        when(conversationRepository.findById(100L)).thenReturn(Optional.of(testConversation));
        when(memberRepository.findByConversationIdAndUserIdAndIsActive(100L, 1L, true))
                .thenReturn(Optional.of(testMember));
        when(userService.getUserById(2L)).thenReturn(testUser1);
        when(friendshipRepository.findByUserIdAndFriendIdAndIsActive(1L, 2L, true))
                .thenReturn(Optional.empty());

        // When & Then
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            conversationService.addMembers(100L, Arrays.asList(2L), 1L);
        });

        assertEquals(50001, exception.getCode());
        assertTrue(exception.getMessage().contains("只能邀请好友加入群聊"));
    }

    @Test
    void testAddMembers_AlreadyMember_Skip() {
        // Given
        when(conversationRepository.findById(100L)).thenReturn(Optional.of(testConversation));
        when(memberRepository.findByConversationIdAndUserIdAndIsActive(100L, 1L, true))
                .thenReturn(Optional.of(testMember));
        when(userService.getUserById(2L)).thenReturn(testUser1);
        when(friendshipRepository.findByUserIdAndFriendIdAndIsActive(1L, 2L, true))
                .thenReturn(Optional.of(testFriendship));
        when(memberRepository.findByConversationIdAndUserIdAndIsActive(100L, 2L, true))
                .thenReturn(Optional.of(new Member()));

        // When
        conversationService.addMembers(100L, Arrays.asList(2L), 1L);

        // Then
        verify(memberRepository, never()).save(any(Member.class));
    }
}