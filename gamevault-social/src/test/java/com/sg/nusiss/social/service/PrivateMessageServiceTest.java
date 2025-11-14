package com.sg.nusiss.social.service;

import com.sg.nusiss.common.dto.UserDTO;
import com.sg.nusiss.common.exception.BusinessException;
import com.sg.nusiss.social.dto.message.request.SendPrivateMessageRequest;
import com.sg.nusiss.social.dto.message.response.MessageResponse;
import com.sg.nusiss.social.entity.friend.Friendship;
import com.sg.nusiss.social.entity.message.Message;
import com.sg.nusiss.social.repository.friend.FriendshipRepository;
import com.sg.nusiss.social.repository.message.MessageRepository;
import com.sg.nusiss.social.service.message.PrivateMessageService;
import com.sg.nusiss.social.service.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PrivateMessageServiceTest {

    @Mock
    private UserService userService;

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private FriendshipRepository friendshipRepository;

    @InjectMocks
    private PrivateMessageService privateMessageService;

    private UserDTO testSender;
    private UserDTO testReceiver;
    private SendPrivateMessageRequest textMessageRequest;
    private SendPrivateMessageRequest fileMessageRequest;
    private Message savedMessage;
    private Friendship friendship;

    @BeforeEach
    void setUp() {
        testSender = UserDTO.builder()
                .userId(1L)
                .username("sender")
                .email("sender@example.com")
                .build();

        testReceiver = UserDTO.builder()
                .userId(2L)
                .username("receiver")
                .email("receiver@example.com")
                .build();

        textMessageRequest = SendPrivateMessageRequest.builder()
                .receiverId(2L)
                .content("Hello World")
                .messageType("text")
                .build();

        fileMessageRequest = SendPrivateMessageRequest.builder()
                .receiverId(2L)
                .content("发送了一个文件")
                .messageType("file")
                .fileId("file123")
                .fileName("test.pdf")
                .fileSize(1024L)
                .fileType("application/pdf")
                .fileExt("pdf")
                .accessUrl("http://example.com/file123")
                .thumbnailUrl("http://example.com/thumb123")
                .build();

        savedMessage = Message.builder()
                .id(1L)
                .senderId(1L)
                .receiverId(2L)
                .content("Hello World")
                .messageType("text")
                .chatType("private")
                .createdAt(LocalDateTime.now())
                .isDeleted(false)
                .build();

        friendship = new Friendship();
        friendship.setId(1L);
        friendship.setUserId(1L);
        friendship.setFriendId(2L);
        friendship.setIsActive(true);
    }

    // ==================== sendPrivateMessage 方法测试 ====================

    @Test
    void testSendPrivateMessage_TextMessage_Success() {
        // Given
        when(userService.getUserById(2L)).thenReturn(testReceiver);
        when(friendshipRepository.findByUserIdAndFriendIdAndIsActive(1L, 2L, true))
                .thenReturn(Optional.of(friendship));
        when(messageRepository.save(any(Message.class))).thenReturn(savedMessage);
        when(userService.getUserById(1L)).thenReturn(testSender);

        // When
        MessageResponse result = privateMessageService.sendPrivateMessage(textMessageRequest, 1L);

        // Then
        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals(1L, result.getSenderId());
        assertEquals(2L, result.getReceiverId());
        assertEquals("sender", result.getSenderUsername());
        assertEquals("Hello World", result.getContent());
        assertEquals("text", result.getMessageType());

        verify(userService, times(1)).getUserById(2L);
        verify(friendshipRepository, times(1)).findByUserIdAndFriendIdAndIsActive(1L, 2L, true);
        verify(messageRepository, times(1)).save(any(Message.class));
    }

    @Test
    void testSendPrivateMessage_FileMessage_Success() {
        // Given
        Message fileMessage = Message.builder()
                .id(2L)
                .senderId(1L)
                .receiverId(2L)
                .content("发送了一个文件")
                .messageType("file")
                .chatType("private")
                .fileId("file123")
                .fileName("test.pdf")
                .fileSize(1024L)
                .fileType("application/pdf")
                .fileExt("pdf")
                .accessUrl("http://example.com/file123")
                .thumbnailUrl("http://example.com/thumb123")
                .createdAt(LocalDateTime.now())
                .isDeleted(false)
                .build();

        when(userService.getUserById(2L)).thenReturn(testReceiver);
        when(friendshipRepository.findByUserIdAndFriendIdAndIsActive(1L, 2L, true))
                .thenReturn(Optional.of(friendship));
        when(messageRepository.save(any(Message.class))).thenReturn(fileMessage);
        when(userService.getUserById(1L)).thenReturn(testSender);

        // When
        MessageResponse result = privateMessageService.sendPrivateMessage(fileMessageRequest, 1L);

        // Then
        assertNotNull(result);
        assertEquals(2L, result.getId());
        assertEquals("file", result.getMessageType());
        assertNotNull(result.getAttachment());
        assertEquals("file123", result.getAttachment().getFileId());
        assertEquals("test.pdf", result.getAttachment().getFileName());
        assertEquals(1024L, result.getAttachment().getFileSize());

        verify(messageRepository, times(1)).save(any(Message.class));
    }

    @Test
    void testSendPrivateMessage_ReceiverNotFound_ThrowException() {
        // Given
        when(userService.getUserById(2L)).thenReturn(null);

        // When & Then
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            privateMessageService.sendPrivateMessage(textMessageRequest, 1L);
        });

        assertEquals(40400, exception.getCode());
        assertEquals("接收者不存在", exception.getMessage());

        verify(userService, times(1)).getUserById(2L);
        verify(friendshipRepository, never()).findByUserIdAndFriendIdAndIsActive(anyLong(), anyLong(), anyBoolean());
        verify(messageRepository, never()).save(any(Message.class));
    }

    @Test
    void testSendPrivateMessage_NotFriend_ThrowException() {
        // Given
        when(userService.getUserById(2L)).thenReturn(testReceiver);
        when(friendshipRepository.findByUserIdAndFriendIdAndIsActive(1L, 2L, true))
                .thenReturn(Optional.empty());

        // When & Then
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            privateMessageService.sendPrivateMessage(textMessageRequest, 1L);
        });

        assertEquals(40101, exception.getCode());
        assertEquals("只能给好友发送消息", exception.getMessage());

        verify(userService, times(1)).getUserById(2L);
        verify(friendshipRepository, times(1)).findByUserIdAndFriendIdAndIsActive(1L, 2L, true);
        verify(messageRepository, never()).save(any(Message.class));
    }

    @Test
    void testSendPrivateMessage_EmptyContent_ThrowException() {
        // Given
        SendPrivateMessageRequest emptyRequest = SendPrivateMessageRequest.builder()
                .receiverId(2L)
                .content("   ")
                .messageType("text")
                .build();

        when(userService.getUserById(2L)).thenReturn(testReceiver);
        when(friendshipRepository.findByUserIdAndFriendIdAndIsActive(1L, 2L, true))
                .thenReturn(Optional.of(friendship));

        // When & Then
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            privateMessageService.sendPrivateMessage(emptyRequest, 1L);
        });

        assertEquals(40000, exception.getCode());
        assertEquals("消息内容不能为空", exception.getMessage());

        verify(messageRepository, never()).save(any(Message.class));
    }

    @Test
    void testSendPrivateMessage_NullContent_ThrowException() {
        // Given
        SendPrivateMessageRequest nullRequest = SendPrivateMessageRequest.builder()
                .receiverId(2L)
                .content(null)
                .messageType("text")
                .build();

        when(userService.getUserById(2L)).thenReturn(testReceiver);
        when(friendshipRepository.findByUserIdAndFriendIdAndIsActive(1L, 2L, true))
                .thenReturn(Optional.of(friendship));

        // When & Then
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            privateMessageService.sendPrivateMessage(nullRequest, 1L);
        });

        assertEquals(40000, exception.getCode());
        assertEquals("消息内容不能为空", exception.getMessage());

        verify(messageRepository, never()).save(any(Message.class));
    }

    // ==================== getPrivateMessages 方法测试 ====================

    @Test
    void testGetPrivateMessages_Success() {
        // Given
        Long userId = 1L;
        Long friendId = 2L;
        int page = 0;
        int size = 10;

        Message msg1 = Message.builder()
                .id(1L)
                .senderId(1L)
                .receiverId(2L)
                .content("Message 1")
                .messageType("text")
                .chatType("private")
                .createdAt(LocalDateTime.now().minusMinutes(5))
                .build();

        Message msg2 = Message.builder()
                .id(2L)
                .senderId(2L)
                .receiverId(1L)
                .content("Message 2")
                .messageType("text")
                .chatType("private")
                .createdAt(LocalDateTime.now().minusMinutes(3))
                .build();

        Page<Message> messagePage = new PageImpl<>(Arrays.asList(msg1, msg2));

        when(friendshipRepository.findByUserIdAndFriendIdAndIsActive(userId, friendId, true))
                .thenReturn(Optional.of(friendship));
        when(messageRepository.findPrivateMessages(eq(userId), eq(friendId), any(Pageable.class)))
                .thenReturn(messagePage);
        when(userService.getUserById(1L)).thenReturn(testSender);
        when(userService.getUserById(2L)).thenReturn(testReceiver);

        // When
        List<MessageResponse> result = privateMessageService.getPrivateMessages(userId, friendId, page, size);

        // Then
        assertNotNull(result);
        assertEquals(2, result.size());

        verify(friendshipRepository, times(1)).findByUserIdAndFriendIdAndIsActive(userId, friendId, true);
        verify(messageRepository, times(1)).findPrivateMessages(eq(userId), eq(friendId), any(Pageable.class));
    }

    @Test
    void testGetPrivateMessages_NotFriend_ThrowException() {
        // Given
        Long userId = 1L;
        Long friendId = 2L;

        when(friendshipRepository.findByUserIdAndFriendIdAndIsActive(userId, friendId, true))
                .thenReturn(Optional.empty());

        // When & Then
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            privateMessageService.getPrivateMessages(userId, friendId, 0, 10);
        });

        assertEquals(40101, exception.getCode());
        assertEquals("只能查看好友的聊天记录", exception.getMessage());

        verify(friendshipRepository, times(1)).findByUserIdAndFriendIdAndIsActive(userId, friendId, true);
        verify(messageRepository, never()).findPrivateMessages(anyLong(), anyLong(), any(Pageable.class));
    }

    @Test
    void testGetPrivateMessages_EmptyResult_Success() {
        // Given
        Long userId = 1L;
        Long friendId = 2L;
        Page<Message> emptyPage = new PageImpl<>(Arrays.asList());

        when(friendshipRepository.findByUserIdAndFriendIdAndIsActive(userId, friendId, true))
                .thenReturn(Optional.of(friendship));
        when(messageRepository.findPrivateMessages(eq(userId), eq(friendId), any(Pageable.class)))
                .thenReturn(emptyPage);

        // When
        List<MessageResponse> result = privateMessageService.getPrivateMessages(userId, friendId, 0, 10);

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());

        verify(messageRepository, times(1)).findPrivateMessages(eq(userId), eq(friendId), any(Pageable.class));
    }

    @Test
    void testGetPrivateMessages_WithFileAttachment_Success() {
        // Given
        Long userId = 1L;
        Long friendId = 2L;

        Message fileMsg = Message.builder()
                .id(3L)
                .senderId(1L)
                .receiverId(2L)
                .content("文件消息")
                .messageType("file")
                .chatType("private")
                .fileId("file123")
                .fileName("test.pdf")
                .fileSize(1024L)
                .fileType("application/pdf")
                .fileExt("pdf")
                .accessUrl("http://example.com/file123")
                .thumbnailUrl("http://example.com/thumb123")
                .createdAt(LocalDateTime.now())
                .build();

        Page<Message> messagePage = new PageImpl<>(Arrays.asList(fileMsg));

        when(friendshipRepository.findByUserIdAndFriendIdAndIsActive(userId, friendId, true))
                .thenReturn(Optional.of(friendship));
        when(messageRepository.findPrivateMessages(eq(userId), eq(friendId), any(Pageable.class)))
                .thenReturn(messagePage);
        when(userService.getUserById(1L)).thenReturn(testSender);

        // When
        List<MessageResponse> result = privateMessageService.getPrivateMessages(userId, friendId, 0, 10);

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());

        MessageResponse response = result.get(0);
        assertEquals("file", response.getMessageType());
        assertNotNull(response.getAttachment());
        assertEquals("file123", response.getAttachment().getFileId());
        assertEquals("test.pdf", response.getAttachment().getFileName());
    }
}
