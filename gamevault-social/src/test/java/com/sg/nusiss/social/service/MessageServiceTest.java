package com.sg.nusiss.social.service;

import com.sg.nusiss.common.dto.UserDTO;
import com.sg.nusiss.common.exception.BusinessException;
import com.sg.nusiss.social.dto.message.request.SendMessageRequest;
import com.sg.nusiss.social.dto.message.response.MessageResponse;
import com.sg.nusiss.social.entity.conversation.Conversation;
import com.sg.nusiss.social.entity.conversation.Member;
import com.sg.nusiss.social.entity.message.Message;
import com.sg.nusiss.social.repository.conversation.ConversationRepository;
import com.sg.nusiss.social.repository.conversation.MemberRepository;
import com.sg.nusiss.social.repository.message.MessageRepository;
import com.sg.nusiss.social.service.cache.MessageCacheService;
import com.sg.nusiss.social.service.message.MessageService;
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
class MessageServiceTest {

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private ConversationRepository conversationRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private MessageCacheService messageCacheService;

    @Mock
    private UserService userService;

    @InjectMocks
    private MessageService messageService;

    private UserDTO testSender;
    private Conversation testConversation;
    private Member testMember;
    private SendMessageRequest textMessageRequest;
    private SendMessageRequest fileMessageRequest;
    private Message savedMessage;

    @BeforeEach
    void setUp() {
        testSender = UserDTO.builder()
                .userId(1L)
                .username("sender")
                .email("sender@example.com")
                .build();

        testConversation = new Conversation();
        testConversation.setId(100L);
        testConversation.setTitle("测试群聊");
        testConversation.setStatus("active");
        testConversation.setOwnerId(1L);

        testMember = new Member();
        testMember.setId(1L);
        testMember.setConversation(testConversation);
        testMember.setUserId(1L);
        testMember.setRole("member");
        testMember.setIsActive(true);

        textMessageRequest = SendMessageRequest.builder()
                .conversationId(100L)
                .content("Hello Group")
                .messageType("text")
                .build();

        fileMessageRequest = SendMessageRequest.builder()
                .conversationId(100L)
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
                .conversationId(100L)
                .senderId(1L)
                .content("Hello Group")
                .messageType("text")
                .createdAt(LocalDateTime.now())
                .isDeleted(false)
                .build();
    }

    // ==================== sendMessage 方法测试 ====================

    @Test
    void testSendMessage_TextMessage_Success() {
        // Given
        when(conversationRepository.findById(100L)).thenReturn(Optional.of(testConversation));
        when(memberRepository.findByConversationIdAndUserIdAndIsActive(100L, 1L, true))
                .thenReturn(Optional.of(testMember));
        when(messageRepository.save(any(Message.class))).thenReturn(savedMessage);
        when(userService.getUserById(1L)).thenReturn(testSender);
        doNothing().when(messageCacheService).cacheMessage(any(MessageResponse.class));

        // When
        MessageResponse result = messageService.sendMessage(textMessageRequest, 1L);

        // Then
        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals(100L, result.getConversationId());
        assertEquals(1L, result.getSenderId());
        assertEquals("sender", result.getSenderUsername());
        assertEquals("Hello Group", result.getContent());
        assertEquals("text", result.getMessageType());

        verify(conversationRepository, times(1)).findById(100L);
        verify(memberRepository, times(1)).findByConversationIdAndUserIdAndIsActive(100L, 1L, true);
        verify(messageRepository, times(1)).save(any(Message.class));
        verify(messageCacheService, times(1)).cacheMessage(any(MessageResponse.class));
    }

    @Test
    void testSendMessage_FileMessage_Success() {
        // Given
        Message fileMessage = Message.builder()
                .id(2L)
                .conversationId(100L)
                .senderId(1L)
                .content("发送了一个文件")
                .messageType("file")
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

        when(conversationRepository.findById(100L)).thenReturn(Optional.of(testConversation));
        when(memberRepository.findByConversationIdAndUserIdAndIsActive(100L, 1L, true))
                .thenReturn(Optional.of(testMember));
        when(messageRepository.save(any(Message.class))).thenReturn(fileMessage);
        when(userService.getUserById(1L)).thenReturn(testSender);
        doNothing().when(messageCacheService).cacheMessage(any(MessageResponse.class));

        // When
        MessageResponse result = messageService.sendMessage(fileMessageRequest, 1L);

        // Then
        assertNotNull(result);
        assertEquals(2L, result.getId());
        assertEquals("file", result.getMessageType());
        assertNotNull(result.getAttachment());
        assertEquals("file123", result.getAttachment().getFileId());
        assertEquals("test.pdf", result.getAttachment().getFileName());

        verify(messageRepository, times(1)).save(any(Message.class));
        verify(messageCacheService, times(1)).cacheMessage(any(MessageResponse.class));
    }

    @Test
    void testSendMessage_ConversationNotFound_ThrowException() {
        // Given
        when(conversationRepository.findById(100L)).thenReturn(Optional.empty());

        // When & Then
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            messageService.sendMessage(textMessageRequest, 1L);
        });

        assertEquals(40400, exception.getCode());
        assertEquals("群聊不存在", exception.getMessage());

        verify(conversationRepository, times(1)).findById(100L);
        verify(memberRepository, never()).findByConversationIdAndUserIdAndIsActive(anyLong(), anyLong(), anyBoolean());
        verify(messageRepository, never()).save(any(Message.class));
    }

    @Test
    void testSendMessage_ConversationDissolved_ThrowException() {
        // Given
        testConversation.setStatus("dissolved");
        when(conversationRepository.findById(100L)).thenReturn(Optional.of(testConversation));

        // When & Then
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            messageService.sendMessage(textMessageRequest, 1L);
        });

        assertEquals(50001, exception.getCode());
        assertEquals("群聊已解散，无法发送消息", exception.getMessage());

        verify(conversationRepository, times(1)).findById(100L);
        verify(messageRepository, never()).save(any(Message.class));
    }

    @Test
    void testSendMessage_NotMember_ThrowException() {
        // Given
        when(conversationRepository.findById(100L)).thenReturn(Optional.of(testConversation));
        when(memberRepository.findByConversationIdAndUserIdAndIsActive(100L, 1L, true))
                .thenReturn(Optional.empty());

        // When & Then
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            messageService.sendMessage(textMessageRequest, 1L);
        });

        assertEquals(40101, exception.getCode());
        assertEquals("您不在该群聊中", exception.getMessage());

        verify(memberRepository, times(1)).findByConversationIdAndUserIdAndIsActive(100L, 1L, true);
        verify(messageRepository, never()).save(any(Message.class));
    }

    @Test
    void testSendMessage_EmptyContent_ThrowException() {
        // Given
        SendMessageRequest emptyRequest = SendMessageRequest.builder()
                .conversationId(100L)
                .content("   ")
                .messageType("text")
                .build();

        when(conversationRepository.findById(100L)).thenReturn(Optional.of(testConversation));
        when(memberRepository.findByConversationIdAndUserIdAndIsActive(100L, 1L, true))
                .thenReturn(Optional.of(testMember));

        // When & Then
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            messageService.sendMessage(emptyRequest, 1L);
        });

        assertEquals(40000, exception.getCode());
        assertEquals("消息内容不能为空", exception.getMessage());

        verify(messageRepository, never()).save(any(Message.class));
    }

    // ==================== getMessages 方法测试 ====================

    @Test
    void testGetMessages_FirstPage_FromCache_Success() {
        // Given
        Long conversationId = 100L;
        Long userId = 1L;
        int page = 0;
        int size = 10;

        MessageResponse cachedMsg1 = MessageResponse.builder()
                .id(1L)
                .conversationId(conversationId)
                .senderId(1L)
                .content("Cached Message 1")
                .messageType("text")
                .build();

        MessageResponse cachedMsg2 = MessageResponse.builder()
                .id(2L)
                .conversationId(conversationId)
                .senderId(1L)
                .content("Cached Message 2")
                .messageType("text")
                .build();

        // 创建包含足够消息的列表（至少 size 个）
        List<MessageResponse> cachedMessages = Arrays.asList(
                cachedMsg1, cachedMsg2, cachedMsg1, cachedMsg2, cachedMsg1,
                cachedMsg2, cachedMsg1, cachedMsg2, cachedMsg1, cachedMsg2
        );

        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(testConversation));
        when(memberRepository.findByConversationIdAndUserIdAndIsActive(conversationId, userId, true))
                .thenReturn(Optional.of(testMember));
        when(messageCacheService.getCachedMessages(conversationId, size)).thenReturn(cachedMessages);

        // When
        List<MessageResponse> result = messageService.getMessages(conversationId, userId, page, size);

        // Then
        assertNotNull(result);
        assertEquals(10, result.size());
        assertEquals("Cached Message 1", result.get(0).getContent());

        verify(messageCacheService, times(1)).getCachedMessages(conversationId, size);
        verify(messageRepository, never()).findByConversationId(anyLong(), any(Pageable.class));
        verify(messageCacheService, never()).batchCacheMessages(anyLong(), anyList());
    }
    @Test
    void testGetMessages_FirstPage_FromDatabase_Success() {
        // Given
        Long conversationId = 100L;
        Long userId = 1L;
        int page = 0;
        int size = 10;

        Message msg1 = Message.builder()
                .id(1L)
                .conversationId(conversationId)
                .senderId(1L)
                .content("DB Message 1")
                .messageType("text")
                .createdAt(LocalDateTime.now())
                .build();

        Message msg2 = Message.builder()
                .id(2L)
                .conversationId(conversationId)
                .senderId(1L)
                .content("DB Message 2")
                .messageType("text")
                .createdAt(LocalDateTime.now())
                .build();

        Page<Message> messagePage = new PageImpl<>(Arrays.asList(msg1, msg2));

        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(testConversation));
        when(memberRepository.findByConversationIdAndUserIdAndIsActive(conversationId, userId, true))
                .thenReturn(Optional.of(testMember));
        when(messageCacheService.getCachedMessages(conversationId, size)).thenReturn(Arrays.asList());
        when(messageRepository.findByConversationId(eq(conversationId), any(Pageable.class)))
                .thenReturn(messagePage);
        when(userService.getUserById(1L)).thenReturn(testSender);
        doNothing().when(messageCacheService).batchCacheMessages(eq(conversationId), anyList());

        // When
        List<MessageResponse> result = messageService.getMessages(conversationId, userId, page, size);

        // Then
        assertNotNull(result);
        assertEquals(2, result.size());

        verify(messageCacheService, times(1)).getCachedMessages(conversationId, size);
        verify(messageRepository, times(1)).findByConversationId(eq(conversationId), any(Pageable.class));
        verify(messageCacheService, times(1)).batchCacheMessages(eq(conversationId), anyList());
    }

    @Test
    void testGetMessages_SecondPage_FromDatabase_Success() {
        // Given
        Long conversationId = 100L;
        Long userId = 1L;
        int page = 1;
        int size = 10;

        Message msg = Message.builder()
                .id(3L)
                .conversationId(conversationId)
                .senderId(1L)
                .content("Page 2 Message")
                .messageType("text")
                .createdAt(LocalDateTime.now())
                .build();

        Page<Message> messagePage = new PageImpl<>(Arrays.asList(msg));

        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(testConversation));
        when(memberRepository.findByConversationIdAndUserIdAndIsActive(conversationId, userId, true))
                .thenReturn(Optional.of(testMember));
        when(messageRepository.findByConversationId(eq(conversationId), any(Pageable.class)))
                .thenReturn(messagePage);
        when(userService.getUserById(1L)).thenReturn(testSender);

        // When
        List<MessageResponse> result = messageService.getMessages(conversationId, userId, page, size);

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());

        verify(messageCacheService, never()).getCachedMessages(anyLong(), anyInt());
        verify(messageRepository, times(1)).findByConversationId(eq(conversationId), any(Pageable.class));
        verify(messageCacheService, never()).batchCacheMessages(anyLong(), anyList());
    }

    @Test
    void testGetMessages_ConversationNotFound_ThrowException() {
        // Given
        Long conversationId = 100L;
        Long userId = 1L;

        when(conversationRepository.findById(conversationId)).thenReturn(Optional.empty());

        // When & Then
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            messageService.getMessages(conversationId, userId, 0, 10);
        });

        assertEquals(40400, exception.getCode());
        assertEquals("群聊不存在", exception.getMessage());

        verify(conversationRepository, times(1)).findById(conversationId);
        verify(memberRepository, never()).findByConversationIdAndUserIdAndIsActive(anyLong(), anyLong(), anyBoolean());
    }

    @Test
    void testGetMessages_NotMember_ThrowException() {
        // Given
        Long conversationId = 100L;
        Long userId = 1L;

        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(testConversation));
        when(memberRepository.findByConversationIdAndUserIdAndIsActive(conversationId, userId, true))
                .thenReturn(Optional.empty());

        // When & Then
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            messageService.getMessages(conversationId, userId, 0, 10);
        });

        assertEquals(40101, exception.getCode());
        assertEquals("您不在该群聊中", exception.getMessage());

        verify(memberRepository, times(1)).findByConversationIdAndUserIdAndIsActive(conversationId, userId, true);
        verify(messageRepository, never()).findByConversationId(anyLong(), any(Pageable.class));
    }

    @Test
    void testGetMessages_WithFileAttachment_Success() {
        // Given
        Long conversationId = 100L;
        Long userId = 1L;

        Message fileMsg = Message.builder()
                .id(4L)
                .conversationId(conversationId)
                .senderId(1L)
                .content("文件消息")
                .messageType("file")
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

        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(testConversation));
        when(memberRepository.findByConversationIdAndUserIdAndIsActive(conversationId, userId, true))
                .thenReturn(Optional.of(testMember));
        when(messageCacheService.getCachedMessages(conversationId, 10)).thenReturn(Arrays.asList());
        when(messageRepository.findByConversationId(eq(conversationId), any(Pageable.class)))
                .thenReturn(messagePage);
        when(userService.getUserById(1L)).thenReturn(testSender);

        // When
        List<MessageResponse> result = messageService.getMessages(conversationId, userId, 0, 10);

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
