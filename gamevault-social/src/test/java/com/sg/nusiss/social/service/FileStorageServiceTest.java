package com.sg.nusiss.social.service;

import com.sg.nusiss.common.exception.BusinessException;
import com.sg.nusiss.social.config.FileUploadProperties;
import com.sg.nusiss.social.dto.file.request.FileUploadRequest;
import com.sg.nusiss.social.dto.file.response.FileUploadResponse;
import com.sg.nusiss.social.entity.file.ChatFileInfo;
import com.sg.nusiss.social.repository.file.ChatFileInfoRepository;
import com.sg.nusiss.social.repository.file.FileChunkInfoRepository;
import com.sg.nusiss.social.repository.file.FileUploadTaskRepository;
import com.sg.nusiss.social.service.file.FileStorageService;
import com.sg.nusiss.social.service.file.MinioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * @ClassName FileStorageServiceTest
 * @Author HUANG ZHENJIA
 * @Date 2025/11/14
 * @Description FileStorageService 单元测试
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FileStorageServiceTest {

    @Mock
    private MinioService minioService;

    @Mock
    private ChatFileInfoRepository fileInfoRepository;

    @Mock
    private FileUploadTaskRepository uploadTaskRepository;

    @Mock
    private FileChunkInfoRepository chunkInfoRepository;

    @Mock
    private FileUploadProperties uploadProperties;

    @Mock
    private FileUploadProperties.QuickUploadConfig quickUploadConfig;

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

    @Mock
    private FileUploadProperties.DocumentConfig documentConfig;

    @Mock
    private MultipartFile multipartFile;

    @InjectMocks
    private FileStorageService fileStorageService;

    private FileUploadRequest uploadRequest;
    private ChatFileInfo testFileInfo;

    @BeforeEach
    void setUp() {
        uploadRequest = FileUploadRequest.builder()
                .fileName("test.jpg")
                .fileSize(1024000L)
                .mimeType("image/jpeg")
                .fileMd5("abc123")
                .bizType("message")
                .bizId("100")
                .build();

        testFileInfo = ChatFileInfo.builder()
                .fileId("existingFile123")
                .fileName("existing.jpg")
                .fileSize(1024000L)
                .fileType("image")
                .mimeType("image/jpeg")
                .fileExt("jpg")
                .bucketName("images")
                .objectKey("image/2025/11/14/file123.jpg")
                .storagePath("images/image/2025/11/14/file123.jpg")
                .fileMd5("abc123")
                .status(1)
                .userId(1L)
                .createdAt(LocalDateTime.now())
                .build();

        // 设置基本的 mock
        when(uploadProperties.getQuickUpload()).thenReturn(quickUploadConfig);
        when(uploadProperties.getChunk()).thenReturn(chunkConfig);
        when(uploadProperties.getPresigned()).thenReturn(presignedConfig);
        when(uploadProperties.getImage()).thenReturn(imageConfig);
        when(uploadProperties.getVideo()).thenReturn(videoConfig);
        when(uploadProperties.getAudio()).thenReturn(audioConfig);
        when(uploadProperties.getDocument()).thenReturn(documentConfig);
    }

    // ==================== uploadFile 方法测试 ====================

    @Test
    void testUploadFile_Success_SmallFile() {
        // Given
        when(quickUploadConfig.getEnabled()).thenReturn(false);
        when(chunkConfig.getMinFileSize()).thenReturn(10485760L);
        when(imageConfig.getAllowedTypesList()).thenReturn(Arrays.asList("jpg", "png"));
        when(imageConfig.getMaxSize()).thenReturn(10485760L);
        when(minioService.getBucketNameByFileType("image")).thenReturn("images");
        when(presignedConfig.getDownloadExpireHours()).thenReturn(24);
        when(minioService.generatePresignedDownloadUrl(anyString(), anyString(), anyInt()))
                .thenReturn("http://minio/download");
        when(fileInfoRepository.save(any(ChatFileInfo.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        FileUploadResponse result = fileStorageService.uploadFile(uploadRequest, multipartFile, 1L);

        // Then
        assertNotNull(result);
        assertNotNull(result.getFileId());
        assertEquals("test.jpg", result.getFileName());
        assertEquals(1024000L, result.getFileSize());
        assertEquals("image", result.getFileType());
        assertFalse(result.getQuickUpload());
        assertFalse(result.getNeedChunkUpload());
        assertEquals("上传成功", result.getMessage());

        verify(fileInfoRepository, times(1)).save(any(ChatFileInfo.class));
    }

    @Test
    void testUploadFile_QuickUpload() {
        // Given
        when(quickUploadConfig.getEnabled()).thenReturn(true);
        when(imageConfig.getAllowedTypesList()).thenReturn(Arrays.asList("jpg", "png"));
        when(imageConfig.getMaxSize()).thenReturn(10485760L);
        when(fileInfoRepository.findFirstByFileMd5AndStatusOrderByCreatedAtDesc("abc123", 1))
                .thenReturn(Optional.of(testFileInfo));
        when(presignedConfig.getDownloadExpireHours()).thenReturn(24);
        when(minioService.generatePresignedDownloadUrl(anyString(), anyString(), anyInt()))
                .thenReturn("http://minio/download");
        when(fileInfoRepository.save(any(ChatFileInfo.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        FileUploadResponse result = fileStorageService.uploadFile(uploadRequest, multipartFile, 1L);

        // Then
        assertNotNull(result);
        assertTrue(result.getQuickUpload());
        assertEquals("文件秒传成功", result.getMessage());

        verify(fileInfoRepository, times(1)).save(any(ChatFileInfo.class));
    }

    @Test
    void testUploadFile_NeedChunkUpload() {
        // Given
        uploadRequest.setFileSize(20971520L);

        when(quickUploadConfig.getEnabled()).thenReturn(false);
        when(chunkConfig.getMinFileSize()).thenReturn(10485760L);
        when(imageConfig.getAllowedTypesList()).thenReturn(Arrays.asList("jpg", "png"));
        when(imageConfig.getMaxSize()).thenReturn(104857600L); // 100MB

        // When
        FileUploadResponse result = fileStorageService.uploadFile(uploadRequest, multipartFile, 1L);

        // Then
        assertNotNull(result);
        assertTrue(result.getNeedChunkUpload());
        assertEquals("文件较大，请使用分片上传", result.getMessage());

        verify(fileInfoRepository, never()).save(any(ChatFileInfo.class));
    }

    @Test
    void testUploadFile_FileTypeNotAllowed_ThrowException() {
        // Given
        uploadRequest.setFileName("test.exe");

        when(quickUploadConfig.getEnabled()).thenReturn(false);
        when(imageConfig.getAllowedTypesList()).thenReturn(Arrays.asList("jpg", "png"));
        when(videoConfig.getAllowedTypesList()).thenReturn(Arrays.asList("mp4", "avi"));
        when(audioConfig.getAllowedTypesList()).thenReturn(Arrays.asList("mp3", "wav"));
        when(documentConfig.getAllowedTypesList()).thenReturn(Arrays.asList("pdf", "docx"));

        // When & Then
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            fileStorageService.uploadFile(uploadRequest, multipartFile, 1L);
        });

        assertEquals(40000, exception.getCode());
        assertTrue(exception.getMessage().contains("不允许上传该类型的文件"));
    }

    @Test
    void testUploadFile_FileSizeExceeded_ThrowException() {
        // Given
        uploadRequest.setFileSize(20971520L);

        when(quickUploadConfig.getEnabled()).thenReturn(false);
        when(imageConfig.getAllowedTypesList()).thenReturn(Arrays.asList("jpg", "png"));
        when(imageConfig.getMaxSize()).thenReturn(10485760L);

        // When & Then
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            fileStorageService.uploadFile(uploadRequest, multipartFile, 1L);
        });

        assertEquals(40000, exception.getCode());
        assertTrue(exception.getMessage().contains("文件大小超过限制"));
    }

    @Test
    void testUploadFile_MinioUploadFailed_ThrowException() {
        // Given
        when(quickUploadConfig.getEnabled()).thenReturn(false);
        when(chunkConfig.getMinFileSize()).thenReturn(10485760L);
        when(imageConfig.getAllowedTypesList()).thenReturn(Arrays.asList("jpg", "png"));
        when(imageConfig.getMaxSize()).thenReturn(10485760L);
        when(minioService.getBucketNameByFileType("image")).thenReturn("images");
        when(minioService.uploadFile(anyString(), anyString(), any(MultipartFile.class)))
                .thenThrow(new RuntimeException("MinIO error"));

        // When & Then
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            fileStorageService.uploadFile(uploadRequest, multipartFile, 1L);
        });

        assertEquals(50001, exception.getCode());
        assertEquals("文件上传失败", exception.getMessage());
    }

    // ==================== generateUploadUrl 方法测试 ====================

    @Test
    void testGenerateUploadUrl_Success() {
        // Given
        when(quickUploadConfig.getEnabled()).thenReturn(false);
        when(chunkConfig.getMinFileSize()).thenReturn(10485760L);
        when(presignedConfig.getUploadExpireMinutes()).thenReturn(30);
        when(minioService.getBucketNameByFileType("image")).thenReturn("images");
        when(minioService.generatePresignedUploadUrl(anyString(), anyString(), eq(30)))
                .thenReturn("http://minio/upload-url");
        when(fileInfoRepository.save(any(ChatFileInfo.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        FileUploadResponse result = fileStorageService.generateUploadUrl(uploadRequest, 1L);

        // Then
        assertNotNull(result);
        assertNotNull(result.getFileId());
        assertEquals("test.jpg", result.getFileName());
        assertEquals("http://minio/upload-url", result.getUploadUrl());
        assertNotNull(result.getUrlExpiresAt());
        assertEquals("请使用提供的URL直接上传文件", result.getMessage());

        verify(minioService, times(1)).generatePresignedUploadUrl(anyString(), anyString(), eq(30));
        verify(fileInfoRepository, times(1)).save(any(ChatFileInfo.class));
    }

    @Test
    void testGenerateUploadUrl_QuickUpload() {
        // Given
        when(quickUploadConfig.getEnabled()).thenReturn(true);
        when(fileInfoRepository.findFirstByFileMd5AndStatusOrderByCreatedAtDesc("abc123", 1))
                .thenReturn(Optional.of(testFileInfo));
        when(presignedConfig.getDownloadExpireHours()).thenReturn(24);
        when(minioService.generatePresignedDownloadUrl(anyString(), anyString(), anyInt()))
                .thenReturn("http://minio/download");
        when(fileInfoRepository.save(any(ChatFileInfo.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        FileUploadResponse result = fileStorageService.generateUploadUrl(uploadRequest, 1L);

        // Then
        assertNotNull(result);
        assertTrue(result.getQuickUpload());
        assertEquals("文件秒传成功", result.getMessage());

        verify(minioService, never()).generatePresignedUploadUrl(anyString(), anyString(), anyInt());
        verify(fileInfoRepository, times(1)).save(any(ChatFileInfo.class));
    }

    @Test
    void testGenerateUploadUrl_NeedChunkUpload() {
        // Given
        uploadRequest.setFileSize(20971520L);

        when(quickUploadConfig.getEnabled()).thenReturn(false);
        when(chunkConfig.getMinFileSize()).thenReturn(10485760L);

        // When
        FileUploadResponse result = fileStorageService.generateUploadUrl(uploadRequest, 1L);

        // Then
        assertNotNull(result);
        assertTrue(result.getNeedChunkUpload());
        assertEquals("文件较大，请使用分片上传", result.getMessage());

        verify(minioService, never()).generatePresignedUploadUrl(anyString(), anyString(), anyInt());
        verify(fileInfoRepository, never()).save(any(ChatFileInfo.class));
    }

    // ==================== 文件类型判断测试 ====================

    @Test
    void testDetermineFileType_ByExtension_Video() {
        // Given
        uploadRequest.setFileName("test.mp4");
        uploadRequest.setMimeType("video/mp4");

        when(quickUploadConfig.getEnabled()).thenReturn(false);
        when(chunkConfig.getMinFileSize()).thenReturn(10485760L);
        when(imageConfig.getAllowedTypesList()).thenReturn(Arrays.asList("jpg", "png"));
        when(videoConfig.getAllowedTypesList()).thenReturn(Arrays.asList("mp4", "avi"));
        when(videoConfig.getMaxSize()).thenReturn(104857600L);
        when(audioConfig.getAllowedTypesList()).thenReturn(Arrays.asList("mp3", "wav"));
        when(documentConfig.getAllowedTypesList()).thenReturn(Arrays.asList("pdf", "docx"));
        when(minioService.getBucketNameByFileType("video")).thenReturn("videos");
        when(presignedConfig.getDownloadExpireHours()).thenReturn(24);
        when(minioService.generatePresignedDownloadUrl(anyString(), anyString(), anyInt()))
                .thenReturn("http://minio/download");
        when(fileInfoRepository.save(any(ChatFileInfo.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        FileUploadResponse result = fileStorageService.uploadFile(uploadRequest, multipartFile, 1L);

        // Then
        assertEquals("video", result.getFileType());
    }

    @Test
    void testDetermineFileType_ByExtension_Document() {
        // Given
        uploadRequest.setFileName("test.pdf");
        uploadRequest.setMimeType("application/pdf");

        when(quickUploadConfig.getEnabled()).thenReturn(false);
        when(chunkConfig.getMinFileSize()).thenReturn(10485760L);
        when(imageConfig.getAllowedTypesList()).thenReturn(Arrays.asList("jpg", "png"));
        when(videoConfig.getAllowedTypesList()).thenReturn(Arrays.asList("mp4", "avi"));
        when(audioConfig.getAllowedTypesList()).thenReturn(Arrays.asList("mp3", "wav"));
        when(documentConfig.getAllowedTypesList()).thenReturn(Arrays.asList("pdf", "docx"));
        when(documentConfig.getMaxSize()).thenReturn(10485760L);
        when(minioService.getBucketNameByFileType("document")).thenReturn("documents");
        when(presignedConfig.getDownloadExpireHours()).thenReturn(24);
        when(minioService.generatePresignedDownloadUrl(anyString(), anyString(), anyInt()))
                .thenReturn("http://minio/download");
        when(fileInfoRepository.save(any(ChatFileInfo.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        FileUploadResponse result = fileStorageService.uploadFile(uploadRequest, multipartFile, 1L);

        // Then
        assertEquals("document", result.getFileType());
    }
}