package com.sg.nusiss.social.service;

import com.sg.nusiss.common.exception.BusinessException;
import com.sg.nusiss.social.config.FileUploadProperties;
import com.sg.nusiss.social.dto.file.request.FileDeleteRequest;
import com.sg.nusiss.social.dto.file.request.FileDownloadRequest;
import com.sg.nusiss.social.dto.file.request.FileListRequest;
import com.sg.nusiss.social.dto.file.response.FileDownloadResponse;
import com.sg.nusiss.social.dto.file.response.FileInfoResponse;
import com.sg.nusiss.social.entity.file.ChatFileInfo;
import com.sg.nusiss.social.entity.file.FileAccessLog;
import com.sg.nusiss.social.repository.file.ChatFileInfoRepository;
import com.sg.nusiss.social.repository.file.FileAccessLogRepository;
import com.sg.nusiss.social.service.file.FileManagementService;
import com.sg.nusiss.social.service.file.MinioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * @ClassName FileManagementServiceTest
 * @Author HUANG ZHENJIA
 * @Date 2025/11/14
 * @Description FileManagementService 单元测试
 */
@ExtendWith(MockitoExtension.class)
class FileManagementServiceTest {

    @Mock
    private MinioService minioService;

    @Mock
    private ChatFileInfoRepository fileInfoRepository;

    @Mock
    private FileAccessLogRepository accessLogRepository;

    @Mock
    private FileUploadProperties uploadProperties;

    @Mock
    private FileUploadProperties.PresignedConfig presignedConfig;

    @InjectMocks
    private FileManagementService fileManagementService;

    private ChatFileInfo testFileInfo;
    private LocalDateTime now;

    @BeforeEach
    void setUp() {
        now = LocalDateTime.now();

        testFileInfo = ChatFileInfo.builder()
                .id(1L)
                .fileId("file123")
                .fileName("test.pdf")
                .fileSize(1024000L)
                .fileType("document")
                .mimeType("application/pdf")
                .fileExt("pdf")
                .bucketName("documents")
                .objectKey("doc/2025/file123.pdf")
                .presignedUrl("http://minio/download")
                .urlExpiresAt(now.plusHours(24))
                .downloadCount(0)
                .status(1)
                .userId(1L)
                .createdAt(now)
                .build();
    }

    // ==================== getFileInfo 方法测试 ====================

    @Test
    void testGetFileInfo_Success_UrlNotExpired() {
        // Given
        when(fileInfoRepository.findByFileId("file123")).thenReturn(Optional.of(testFileInfo));

        // When
        FileInfoResponse result = fileManagementService.getFileInfo("file123", 1L);

        // Then
        assertNotNull(result);
        assertEquals("file123", result.getFileId());
        assertEquals("test.pdf", result.getFileName());
        assertEquals(1024000L, result.getFileSize());
        assertEquals("http://minio/download", result.getDownloadUrl());

        verify(fileInfoRepository, times(1)).findByFileId("file123");
        verify(fileInfoRepository, never()).save(any(ChatFileInfo.class));
    }

    @Test
    void testGetFileInfo_Success_UrlExpired_RegenerateUrl() {
        // Given
        testFileInfo.setUrlExpiresAt(now.minusHours(1));

        when(uploadProperties.getPresigned()).thenReturn(presignedConfig);
        when(fileInfoRepository.findByFileId("file123")).thenReturn(Optional.of(testFileInfo));
        when(presignedConfig.getDownloadExpireHours()).thenReturn(24);
        when(minioService.generatePresignedDownloadUrl(anyString(), anyString(), anyInt()))
                .thenReturn("http://minio/new-download");
        when(fileInfoRepository.save(any(ChatFileInfo.class))).thenReturn(testFileInfo);

        // When
        FileInfoResponse result = fileManagementService.getFileInfo("file123", 1L);

        // Then
        assertNotNull(result);
        assertEquals("file123", result.getFileId());
        assertEquals("http://minio/new-download", result.getDownloadUrl());

        verify(minioService, times(1)).generatePresignedDownloadUrl(anyString(), anyString(), eq(1440));
        verify(fileInfoRepository, times(1)).save(any(ChatFileInfo.class));
    }

    @Test
    void testGetFileInfo_FileNotFound_ThrowException() {
        // Given
        when(fileInfoRepository.findByFileId("file123")).thenReturn(Optional.empty());

        // When & Then
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            fileManagementService.getFileInfo("file123", 1L);
        });

        assertEquals(40400, exception.getCode());
        assertEquals("文件不存在", exception.getMessage());
    }

    // ==================== getDownloadUrl 方法测试 ====================

    @Test
    void testGetDownloadUrl_Success() {
        // Given
        FileDownloadRequest request = FileDownloadRequest.builder()
                .fileId("file123")
                .expiresInMinutes(60)
                .recordLog(true)
                .build();

        when(fileInfoRepository.findByFileId("file123")).thenReturn(Optional.of(testFileInfo));
        when(minioService.generatePresignedDownloadUrl(anyString(), anyString(), eq(60)))
                .thenReturn("http://minio/download-url");
        when(fileInfoRepository.save(any(ChatFileInfo.class))).thenReturn(testFileInfo);
        when(accessLogRepository.save(any(FileAccessLog.class))).thenReturn(new FileAccessLog());

        // When
        FileDownloadResponse result = fileManagementService.getDownloadUrl(request, 1L, "192.168.1.1");

        // Then
        assertNotNull(result);
        assertEquals("file123", result.getFileId());
        assertEquals("test.pdf", result.getFileName());
        assertEquals("http://minio/download-url", result.getDownloadUrl());

        verify(fileInfoRepository, times(1)).save(any(ChatFileInfo.class));
        verify(accessLogRepository, times(1)).save(any(FileAccessLog.class));
    }

    @Test
    void testGetDownloadUrl_UseDefaultExpiration() {
        // Given
        FileDownloadRequest request = FileDownloadRequest.builder()
                .fileId("file123")
                .recordLog(false)
                .build();

        when(uploadProperties.getPresigned()).thenReturn(presignedConfig);
        when(fileInfoRepository.findByFileId("file123")).thenReturn(Optional.of(testFileInfo));
        when(presignedConfig.getDownloadExpireHours()).thenReturn(24);
        when(minioService.generatePresignedDownloadUrl(anyString(), anyString(), eq(1440)))
                .thenReturn("http://minio/download-url");
        when(fileInfoRepository.save(any(ChatFileInfo.class))).thenReturn(testFileInfo);

        // When
        FileDownloadResponse result = fileManagementService.getDownloadUrl(request, 1L, "192.168.1.1");

        // Then
        assertNotNull(result);
        verify(minioService, times(1)).generatePresignedDownloadUrl(anyString(), anyString(), eq(1440));
        verify(accessLogRepository, never()).save(any(FileAccessLog.class));
    }

    @Test
    void testGetDownloadUrl_FileNotFound_ThrowException() {
        // Given
        FileDownloadRequest request = FileDownloadRequest.builder()
                .fileId("file123")
                .build();

        when(fileInfoRepository.findByFileId("file123")).thenReturn(Optional.empty());

        // When & Then
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            fileManagementService.getDownloadUrl(request, 1L, "192.168.1.1");
        });

        assertEquals(40400, exception.getCode());
    }

    // ==================== listFiles 方法测试 ====================

    @Test
    void testListFiles_ByBizTypeAndBizId() {
        // Given
        FileListRequest request = FileListRequest.builder()
                .bizType("message")
                .bizId("100")
                .status(1)
                .page(0)
                .size(10)
                .sortBy("createdAt")
                .sortDir("desc")
                .build();

        List<ChatFileInfo> files = Arrays.asList(testFileInfo);
        when(fileInfoRepository.findByBizTypeAndBizIdAndStatusOrderByCreatedAtDesc("message", "100", 1))
                .thenReturn(files);

        // When
        Page<FileInfoResponse> result = fileManagementService.listFiles(request, 1L);

        // Then
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals("file123", result.getContent().get(0).getFileId());

        verify(fileInfoRepository, times(1))
                .findByBizTypeAndBizIdAndStatusOrderByCreatedAtDesc("message", "100", 1);
    }

    @Test
    void testListFiles_ByFileType() {
        // Given
        FileListRequest request = FileListRequest.builder()
                .fileType("document")
                .status(1)
                .page(0)
                .size(10)
                .sortBy("createdAt")
                .sortDir("desc")
                .build();

        List<ChatFileInfo> files = Arrays.asList(testFileInfo);
        when(fileInfoRepository.findByUserIdAndFileTypeAndStatusOrderByCreatedAtDesc(1L, "document", 1))
                .thenReturn(files);

        // When
        Page<FileInfoResponse> result = fileManagementService.listFiles(request, 1L);

        // Then
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());

        verify(fileInfoRepository, times(1))
                .findByUserIdAndFileTypeAndStatusOrderByCreatedAtDesc(1L, "document", 1);
    }

    @Test
    void testListFiles_AllFiles() {
        // Given
        FileListRequest request = FileListRequest.builder()
                .status(1)
                .page(0)
                .size(10)
                .sortBy("createdAt")
                .sortDir("desc")
                .build();

        List<ChatFileInfo> files = Arrays.asList(testFileInfo);
        when(fileInfoRepository.findByUserIdAndStatusOrderByCreatedAtDesc(1L, 1))
                .thenReturn(files);

        // When
        Page<FileInfoResponse> result = fileManagementService.listFiles(request, 1L);

        // Then
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());

        verify(fileInfoRepository, times(1))
                .findByUserIdAndStatusOrderByCreatedAtDesc(1L, 1);
    }

    // ==================== deleteFile 方法测试 ====================

    @Test
    void testDeleteFile_LogicalDelete_Success() {
        // Given
        FileDeleteRequest request = FileDeleteRequest.builder()
                .fileId("file123")
                .physicalDelete(false)
                .build();

        when(fileInfoRepository.findByFileId("file123")).thenReturn(Optional.of(testFileInfo));
        when(fileInfoRepository.save(any(ChatFileInfo.class))).thenReturn(testFileInfo);
        when(accessLogRepository.save(any(FileAccessLog.class))).thenReturn(new FileAccessLog());

        // When
        fileManagementService.deleteFile(request, 1L, "192.168.1.1");

        // Then
        verify(fileInfoRepository, times(1)).save(any(ChatFileInfo.class));
        verify(minioService, never()).deleteFile(anyString(), anyString());
        verify(fileInfoRepository, never()).delete(any(ChatFileInfo.class));
    }

    @Test
    void testDeleteFile_PhysicalDelete_Success() {
        // Given
        FileDeleteRequest request = FileDeleteRequest.builder()
                .fileId("file123")
                .physicalDelete(true)
                .build();

        when(fileInfoRepository.findByFileId("file123")).thenReturn(Optional.of(testFileInfo));
        doNothing().when(minioService).deleteFile(anyString(), anyString());
        doNothing().when(fileInfoRepository).delete(any(ChatFileInfo.class));
        when(accessLogRepository.save(any(FileAccessLog.class))).thenReturn(new FileAccessLog());

        // When
        fileManagementService.deleteFile(request, 1L, "192.168.1.1");

        // Then
        verify(minioService, times(1)).deleteFile("documents", "doc/2025/file123.pdf");
        verify(fileInfoRepository, times(1)).delete(testFileInfo);
    }

    @Test
    void testDeleteFile_FileNotFound_ThrowException() {
        // Given
        FileDeleteRequest request = FileDeleteRequest.builder()
                .fileId("file123")
                .build();

        when(fileInfoRepository.findByFileId("file123")).thenReturn(Optional.empty());

        // When & Then
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            fileManagementService.deleteFile(request, 1L, "192.168.1.1");
        });

        assertEquals(40400, exception.getCode());
    }

    @Test
    void testDeleteFile_NoPermission_ThrowException() {
        // Given
        FileDeleteRequest request = FileDeleteRequest.builder()
                .fileId("file123")
                .build();

        when(fileInfoRepository.findByFileId("file123")).thenReturn(Optional.of(testFileInfo));

        // When & Then
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            fileManagementService.deleteFile(request, 999L, "192.168.1.1");
        });

        assertEquals(40101, exception.getCode());
        assertEquals("无权删除此文件", exception.getMessage());
    }

    @Test
    void testDeleteFile_PhysicalDelete_MinioFailed_ThrowException() {
        // Given
        FileDeleteRequest request = FileDeleteRequest.builder()
                .fileId("file123")
                .physicalDelete(true)
                .build();

        when(fileInfoRepository.findByFileId("file123")).thenReturn(Optional.of(testFileInfo));
        doThrow(new RuntimeException("MinIO error")).when(minioService).deleteFile(anyString(), anyString());

        // When & Then
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            fileManagementService.deleteFile(request, 1L, "192.168.1.1");
        });

        assertEquals(50001, exception.getCode());
        assertEquals("文件删除失败", exception.getMessage());
    }

    // ==================== batchDeleteFiles 方法测试 ====================

    @Test
    void testBatchDeleteFiles_Success() {
        // Given
        List<String> fileIds = Arrays.asList("file1", "file2");

        ChatFileInfo file1 = ChatFileInfo.builder()
                .fileId("file1")
                .userId(1L)
                .bucketName("bucket1")
                .objectKey("key1")
                .build();

        ChatFileInfo file2 = ChatFileInfo.builder()
                .fileId("file2")
                .userId(1L)
                .bucketName("bucket2")
                .objectKey("key2")
                .build();

        when(fileInfoRepository.findByFileId("file1")).thenReturn(Optional.of(file1));
        when(fileInfoRepository.findByFileId("file2")).thenReturn(Optional.of(file2));
        when(fileInfoRepository.save(any(ChatFileInfo.class))).thenReturn(file1);
        when(accessLogRepository.save(any(FileAccessLog.class))).thenReturn(new FileAccessLog());

        // When
        fileManagementService.batchDeleteFiles(fileIds, 1L, false);

        // Then
        verify(fileInfoRepository, times(2)).save(any(ChatFileInfo.class));
    }

    @Test
    void testBatchDeleteFiles_SomeFilesFail_ContinueProcessing() {
        // Given
        List<String> fileIds = Arrays.asList("file1", "file2");

        when(fileInfoRepository.findByFileId("file1")).thenReturn(Optional.empty());
        when(fileInfoRepository.findByFileId("file2")).thenReturn(Optional.of(testFileInfo));
        when(fileInfoRepository.save(any(ChatFileInfo.class))).thenReturn(testFileInfo);
        when(accessLogRepository.save(any(FileAccessLog.class))).thenReturn(new FileAccessLog());

        // When & Then
        assertDoesNotThrow(() -> {
            fileManagementService.batchDeleteFiles(fileIds, 1L, false);
        });
    }

    // ==================== getUserFileStats 方法测试 ====================

    @Test
    void testGetUserFileStats_Success() {
        // Given
        when(fileInfoRepository.sumFileSizeByUserId(1L)).thenReturn(5242880L);
        when(fileInfoRepository.countByUserIdAndStatus(1L, 1)).thenReturn(10L);

        // When
        FileManagementService.UserFileStats result = fileManagementService.getUserFileStats(1L);

        // Then
        assertNotNull(result);
        assertEquals(10L, result.getTotalFiles());
        assertEquals(5242880L, result.getTotalSize());
        assertEquals("5.00 MB", result.getTotalSizeFormatted());

        verify(fileInfoRepository, times(1)).sumFileSizeByUserId(1L);
        verify(fileInfoRepository, times(1)).countByUserIdAndStatus(1L, 1);
    }

    @Test
    void testGetUserFileStats_NoFiles() {
        // Given
        when(fileInfoRepository.sumFileSizeByUserId(1L)).thenReturn(null);
        when(fileInfoRepository.countByUserIdAndStatus(1L, 1)).thenReturn(0L);

        // When
        FileManagementService.UserFileStats result = fileManagementService.getUserFileStats(1L);

        // Then
        assertNotNull(result);
        assertEquals(0L, result.getTotalFiles());
        assertEquals(0L, result.getTotalSize());
        assertEquals("0 B", result.getTotalSizeFormatted());
    }

    // ==================== UserFileStats 类测试 ====================

    @Test
    void testUserFileStats_Builder() {
        // When
        FileManagementService.UserFileStats stats = FileManagementService.UserFileStats.builder()
                .totalFiles(10L)
                .totalSize(1024000L)
                .totalSizeFormatted("1000.00 KB")
                .build();

        // Then
        assertEquals(10L, stats.getTotalFiles());
        assertEquals(1024000L, stats.getTotalSize());
        assertEquals("1000.00 KB", stats.getTotalSizeFormatted());
    }
}