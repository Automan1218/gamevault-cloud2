package com.sg.nusiss.social.service;

import com.sg.nusiss.common.exception.BusinessException;
import com.sg.nusiss.social.config.FileUploadProperties;
import com.sg.nusiss.social.dto.file.request.CompleteChunkUploadRequest;
import com.sg.nusiss.social.dto.file.request.InitChunkUploadRequest;
import com.sg.nusiss.social.dto.file.response.CompleteChunkUploadResponse;
import com.sg.nusiss.social.dto.file.response.InitChunkUploadResponse;
import com.sg.nusiss.social.dto.file.response.UploadTaskResponse;
import com.sg.nusiss.social.entity.file.ChatFileInfo;
import com.sg.nusiss.social.entity.file.FileChunkInfo;
import com.sg.nusiss.social.entity.file.FileUploadTask;
import com.sg.nusiss.social.repository.file.ChatFileInfoRepository;
import com.sg.nusiss.social.repository.file.FileChunkInfoRepository;
import com.sg.nusiss.social.repository.file.FileUploadTaskRepository;
import com.sg.nusiss.social.service.file.ChunkUploadService;
import com.sg.nusiss.social.service.file.MinioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * @ClassName ChunkUploadServiceTest
 * @Author HUANG ZHENJIA
 * @Date 2025/11/14
 * @Description ChunkUploadService 单元测试
 */
@ExtendWith(MockitoExtension.class)
class ChunkUploadServiceTest {

    @Mock
    private MinioService minioService;

    @Mock
    private FileUploadTaskRepository uploadTaskRepository;

    @Mock
    private FileChunkInfoRepository chunkInfoRepository;

    @Mock
    private ChatFileInfoRepository fileInfoRepository;

    @Mock
    private FileUploadProperties uploadProperties;

    @Mock
    private FileUploadProperties.ConcurrentConfig concurrentConfig;

    @Mock
    private FileUploadProperties.ChunkConfig chunkConfig;

    @Mock
    private FileUploadProperties.PresignedConfig presignedConfig;

    @Mock
    private FileUploadProperties.ImageConfig imageConfig;

    @Mock
    private FileUploadProperties.VideoConfig videoConfig;

    @Mock
    private FileUploadProperties.AudioConfig audioConfig;

    @InjectMocks
    private ChunkUploadService chunkUploadService;

    private InitChunkUploadRequest initRequest;
    private FileUploadTask testTask;
    private CompleteChunkUploadRequest completeRequest;

    @BeforeEach
    void setUp() {
        initRequest = new InitChunkUploadRequest();
        initRequest.setFileName("test.pdf");
        initRequest.setFileSize(10485760L);
        initRequest.setChunkSize(1048576);
        initRequest.setTotalChunks(10);
        initRequest.setFileMd5("abc123");
        initRequest.setMimeType("application/pdf");

        testTask = FileUploadTask.builder()
                .id(1L)
                .taskId("task123")
                .fileMd5("abc123")
                .fileName("test.pdf")
                .fileSize(10485760L)
                .chunkSize(1048576)
                .totalChunks(10)
                .uploadedChunks(0)
                .bucketName("documents")
                .objectKey("document/2025/11/14/task123.pdf")
                .status(1)
                .userId(1L)
                .expiresAt(LocalDateTime.now().plusHours(24))
                .createdAt(LocalDateTime.now())
                .build();

        CompleteChunkUploadRequest.ChunkInfo chunk1 = new CompleteChunkUploadRequest.ChunkInfo();
        chunk1.setChunkNumber(1);
        chunk1.setEtag("etag1");

        CompleteChunkUploadRequest.ChunkInfo chunk2 = new CompleteChunkUploadRequest.ChunkInfo();
        chunk2.setChunkNumber(2);
        chunk2.setEtag("etag2");

        completeRequest = new CompleteChunkUploadRequest();
        completeRequest.setTaskId("task123");
        completeRequest.setChunks(Arrays.asList(chunk1, chunk2));
    }

    // ==================== initChunkUpload 方法测试 ====================

    @Test
    void testInitChunkUpload_Success() {
        // Given
        Long userId = 1L;

        when(uploadProperties.getConcurrent()).thenReturn(concurrentConfig);
        when(uploadProperties.getChunk()).thenReturn(chunkConfig);
        when(uploadProperties.getPresigned()).thenReturn(presignedConfig);
        when(uploadProperties.getImage()).thenReturn(imageConfig);
        when(uploadProperties.getVideo()).thenReturn(videoConfig);
        when(uploadProperties.getAudio()).thenReturn(audioConfig);

        when(concurrentConfig.getMaxUploadsPerUser()).thenReturn(5);
        when(uploadTaskRepository.countByUserIdAndStatus(userId, 1)).thenReturn(0L);
        when(uploadTaskRepository.findByFileMd5AndStatus("abc123", 1)).thenReturn(Optional.empty());
        when(chunkConfig.getTaskExpireHours()).thenReturn(24);
        when(presignedConfig.getUploadExpireMinutes()).thenReturn(30);
        when(imageConfig.getAllowedTypesList()).thenReturn(Arrays.asList("jpg", "png"));
        when(videoConfig.getAllowedTypesList()).thenReturn(Arrays.asList("mp4", "avi"));
        when(audioConfig.getAllowedTypesList()).thenReturn(Arrays.asList("mp3", "wav"));
        when(minioService.getBucketNameByFileType("document")).thenReturn("documents");
        when(minioService.generatePresignedUploadPartUrl(anyString(), anyString(), anyInt(), anyInt()))
                .thenReturn("http://minio/upload-url");
        when(uploadTaskRepository.save(any(FileUploadTask.class))).thenReturn(testTask);
        when(chunkInfoRepository.save(any(FileChunkInfo.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        InitChunkUploadResponse result = chunkUploadService.initChunkUpload(initRequest, userId);

        // Then
        assertNotNull(result);
        assertNotNull(result.getTaskId());
        assertEquals("test.pdf", result.getFileName());
        assertEquals(10, result.getTotalChunks());
        assertEquals(10, result.getChunkUploadUrls().size());

        verify(uploadTaskRepository, times(1)).save(any(FileUploadTask.class));
        verify(chunkInfoRepository, times(10)).save(any(FileChunkInfo.class));
    }

    @Test
    void testInitChunkUpload_ExceedConcurrentLimit_ThrowException() {
        // Given
        Long userId = 1L;

        when(uploadProperties.getConcurrent()).thenReturn(concurrentConfig);
        when(concurrentConfig.getMaxUploadsPerUser()).thenReturn(5);
        when(uploadTaskRepository.countByUserIdAndStatus(userId, 1)).thenReturn(5L);

        // When & Then
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            chunkUploadService.initChunkUpload(initRequest, userId);
        });

        assertTrue(exception.getMessage().contains("同时上传的文件数量已达上限"));

        verify(uploadTaskRepository, never()).save(any(FileUploadTask.class));
    }

    @Test
    void testInitChunkUpload_ExistingTask_ReturnExisting() {
        // Given
        Long userId = 1L;

        FileChunkInfo chunk1 = FileChunkInfo.builder()
                .chunkNumber(1)
                .uploadUrl("http://minio/url1")
                .urlExpiresAt(LocalDateTime.now().plusMinutes(30))
                .build();

        when(uploadProperties.getConcurrent()).thenReturn(concurrentConfig);
        when(concurrentConfig.getMaxUploadsPerUser()).thenReturn(5);
        when(uploadTaskRepository.countByUserIdAndStatus(userId, 1)).thenReturn(0L);
        when(uploadTaskRepository.findByFileMd5AndStatus("abc123", 1)).thenReturn(Optional.of(testTask));
        when(chunkInfoRepository.findByTaskIdOrderByChunkNumber("task123")).thenReturn(Arrays.asList(chunk1));

        // When
        InitChunkUploadResponse result = chunkUploadService.initChunkUpload(initRequest, userId);

        // Then
        assertNotNull(result);
        assertEquals("task123", result.getTaskId());
        assertTrue(result.getMessage().contains("已存在"));

        verify(uploadTaskRepository, never()).save(any(FileUploadTask.class));
    }

    // ==================== completeChunkUpload 方法测试 ====================

    @Test
    void testCompleteChunkUpload_Success() {
        // Given
        Long userId = 1L;
        testTask.setTotalChunks(2);

        when(uploadProperties.getPresigned()).thenReturn(presignedConfig);
        when(uploadProperties.getImage()).thenReturn(imageConfig);
        when(uploadProperties.getVideo()).thenReturn(videoConfig);
        when(uploadProperties.getAudio()).thenReturn(audioConfig);

        when(uploadTaskRepository.findByTaskId("task123")).thenReturn(Optional.of(testTask));
        when(chunkInfoRepository.countByTaskIdAndStatus("task123", 3)).thenReturn(2L);
        when(minioService.mergeChunks("documents", "document/2025/11/14/task123.pdf", 2))
                .thenReturn("document/2025/11/14/task123.pdf");
        when(uploadTaskRepository.save(any(FileUploadTask.class))).thenReturn(testTask);
        when(fileInfoRepository.save(any(ChatFileInfo.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(presignedConfig.getDownloadExpireHours()).thenReturn(24);
        when(minioService.generatePresignedDownloadUrl(anyString(), anyString(), anyInt()))
                .thenReturn("http://minio/download-url");
        when(imageConfig.getAllowedTypesList()).thenReturn(Arrays.asList("jpg", "png"));
        when(videoConfig.getAllowedTypesList()).thenReturn(Arrays.asList("mp4", "avi"));
        when(audioConfig.getAllowedTypesList()).thenReturn(Arrays.asList("mp3", "wav"));

        // When
        CompleteChunkUploadResponse result = chunkUploadService.completeChunkUpload(completeRequest, userId);

        // Then
        assertNotNull(result);
        assertNotNull(result.getFileId());
        assertEquals("test.pdf", result.getFileName());
        assertEquals("success", result.getStatus());

        verify(chunkInfoRepository, times(2)).updateChunkStatus(anyString(), anyInt(), eq(3), anyString(), any());
        verify(uploadTaskRepository, times(1)).save(any(FileUploadTask.class));
        verify(fileInfoRepository, times(1)).save(any(ChatFileInfo.class));
    }

    @Test
    void testCompleteChunkUpload_TaskNotFound_ThrowException() {
        // Given
        when(uploadTaskRepository.findByTaskId("task123")).thenReturn(Optional.empty());

        // When & Then
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            chunkUploadService.completeChunkUpload(completeRequest, 1L);
        });

        assertEquals(40400, exception.getCode());
        assertEquals("上传任务不存在", exception.getMessage());
    }

    @Test
    void testCompleteChunkUpload_NoPermission_ThrowException() {
        // Given
        when(uploadTaskRepository.findByTaskId("task123")).thenReturn(Optional.of(testTask));

        // When & Then
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            chunkUploadService.completeChunkUpload(completeRequest, 999L);
        });

        assertEquals(40101, exception.getCode());
        assertEquals("无权操作此上传任务", exception.getMessage());
    }

    @Test
    void testCompleteChunkUpload_InvalidStatus_ThrowException() {
        // Given
        testTask.setStatus(2);
        when(uploadTaskRepository.findByTaskId("task123")).thenReturn(Optional.of(testTask));

        // When & Then
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            chunkUploadService.completeChunkUpload(completeRequest, 1L);
        });

        assertEquals(50001, exception.getCode());
        assertTrue(exception.getMessage().contains("任务状态异常"));
    }

    @Test
    void testCompleteChunkUpload_IncompleteChunks_ThrowException() {
        // Given
        testTask.setTotalChunks(10);
        when(uploadTaskRepository.findByTaskId("task123")).thenReturn(Optional.of(testTask));
        when(chunkInfoRepository.countByTaskIdAndStatus("task123", 3)).thenReturn(2L);

        // When & Then
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            chunkUploadService.completeChunkUpload(completeRequest, 1L);
        });

        assertEquals(50001, exception.getCode());
        assertTrue(exception.getMessage().contains("分片上传不完整"));
    }

    @Test
    void testCompleteChunkUpload_MergeFailed_ThrowException() {
        // Given
        testTask.setTotalChunks(2);
        when(uploadTaskRepository.findByTaskId("task123")).thenReturn(Optional.of(testTask));
        when(chunkInfoRepository.countByTaskIdAndStatus("task123", 3)).thenReturn(2L);
        when(minioService.mergeChunks(anyString(), anyString(), anyInt()))
                .thenThrow(new RuntimeException("Merge failed"));

        // When & Then
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            chunkUploadService.completeChunkUpload(completeRequest, 1L);
        });

        assertEquals(50001, exception.getCode());
        assertEquals("分片合并失败", exception.getMessage());

        verify(uploadTaskRepository, times(1)).updateStatusByTaskId(eq("task123"), eq(4), any());
    }

    // ==================== getTaskStatus 方法测试 ====================

    @Test
    void testGetTaskStatus_Success() {
        // Given
        testTask.setUploadedChunks(5);
        when(uploadTaskRepository.findByTaskId("task123")).thenReturn(Optional.of(testTask));

        // When
        UploadTaskResponse result = chunkUploadService.getTaskStatus("task123", 1L);

        // Then
        assertNotNull(result);
        assertEquals("task123", result.getTaskId());
        assertEquals("test.pdf", result.getFileName());
        assertEquals(50.0, result.getProgress());
        assertEquals("上传中", result.getStatusDesc());

        verify(uploadTaskRepository, times(1)).findByTaskId("task123");
    }

    @Test
    void testGetTaskStatus_TaskNotFound_ThrowException() {
        // Given
        when(uploadTaskRepository.findByTaskId("task123")).thenReturn(Optional.empty());

        // When & Then
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            chunkUploadService.getTaskStatus("task123", 1L);
        });

        assertEquals(40400, exception.getCode());
        assertEquals("上传任务不存在", exception.getMessage());
    }

    @Test
    void testGetTaskStatus_NoPermission_ThrowException() {
        // Given
        when(uploadTaskRepository.findByTaskId("task123")).thenReturn(Optional.of(testTask));

        // When & Then
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            chunkUploadService.getTaskStatus("task123", 999L);
        });

        assertEquals(40101, exception.getCode());
        assertEquals("无权查看此上传任务", exception.getMessage());
    }

    // ==================== cancelUploadTask 方法测试 ====================

    @Test
    void testCancelUploadTask_Success() {
        // Given
        when(uploadTaskRepository.findByTaskId("task123")).thenReturn(Optional.of(testTask));
        when(minioService.fileExists(anyString(), anyString())).thenReturn(true);
        doNothing().when(minioService).deleteFile(anyString(), anyString());

        // When
        chunkUploadService.cancelUploadTask("task123", 1L);

        // Then
        verify(uploadTaskRepository, times(1)).updateStatusByTaskId(eq("task123"), eq(3), any());
        verify(minioService, times(10)).fileExists(anyString(), anyString());
    }

    @Test
    void testCancelUploadTask_TaskNotFound_ThrowException() {
        // Given
        when(uploadTaskRepository.findByTaskId("task123")).thenReturn(Optional.empty());

        // When & Then
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            chunkUploadService.cancelUploadTask("task123", 1L);
        });

        assertEquals(40400, exception.getCode());
        assertEquals("上传任务不存在", exception.getMessage());
    }

    @Test
    void testCancelUploadTask_NoPermission_ThrowException() {
        // Given
        when(uploadTaskRepository.findByTaskId("task123")).thenReturn(Optional.of(testTask));

        // When & Then
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            chunkUploadService.cancelUploadTask("task123", 999L);
        });

        assertEquals(40101, exception.getCode());
        assertEquals("无权操作此上传任务", exception.getMessage());
    }

    @Test
    void testCancelUploadTask_CleanupFailed_NoThrow() {
        // Given
        when(uploadTaskRepository.findByTaskId("task123")).thenReturn(Optional.of(testTask));
        when(minioService.fileExists(anyString(), anyString())).thenThrow(new RuntimeException("Cleanup failed"));

        // When & Then
        assertDoesNotThrow(() -> {
            chunkUploadService.cancelUploadTask("task123", 1L);
        });

        verify(uploadTaskRepository, times(1)).updateStatusByTaskId(eq("task123"), eq(3), any());
    }
}