package com.sg.nusiss.social.service;

import com.sg.nusiss.social.config.MinioConfig;
import com.sg.nusiss.social.service.file.MinioService;
import io.minio.*;
import io.minio.messages.Item;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * @ClassName MinioServiceTest
 * @Author HUANG ZHENJIA
 * @Date 2025/11/14
 * @Description MinioService 单元测试
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MinioServiceTest {

    @Mock
    private MinioClient minioClient;

    @Mock
    private MinioClient publicMinioClient;

    @Mock
    private MinioConfig minioConfig;

    @Mock
    private MultipartFile multipartFile;

    @Mock
    private GetObjectResponse getObjectResponse;

    private MinioService minioService;

    @BeforeEach
    void setUp() {
        minioService = new MinioService(minioClient, publicMinioClient, minioConfig);
    }

    // ==================== ensureBucketExists 方法测试 ====================

    @Test
    void testEnsureBucketExists_BucketExists() throws Exception {
        // Given
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);

        // When
        minioService.ensureBucketExists("test-bucket");

        // Then
        verify(minioClient, times(1)).bucketExists(any(BucketExistsArgs.class));
        verify(minioClient, never()).makeBucket(any(MakeBucketArgs.class));
    }

    @Test
    void testEnsureBucketExists_BucketNotExists_CreateSuccess() throws Exception {
        // Given
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(false);
        doNothing().when(minioClient).makeBucket(any(MakeBucketArgs.class));

        // When
        minioService.ensureBucketExists("test-bucket");

        // Then
        verify(minioClient, times(1)).bucketExists(any(BucketExistsArgs.class));
        verify(minioClient, times(1)).makeBucket(any(MakeBucketArgs.class));
    }

    @Test
    void testEnsureBucketExists_Exception_ThrowRuntimeException() throws Exception {
        // Given
        when(minioClient.bucketExists(any(BucketExistsArgs.class)))
                .thenThrow(new RuntimeException("MinIO error"));

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            minioService.ensureBucketExists("test-bucket");
        });

        assertTrue(exception.getMessage().contains("无法创建存储桶"));
    }

    // ==================== uploadFile (MultipartFile) 方法测试 ====================

    @Test
    void testUploadFile_MultipartFile_Success() throws Exception {
        // Given
        InputStream inputStream = new ByteArrayInputStream("test content".getBytes());
        when(multipartFile.getInputStream()).thenReturn(inputStream);
        when(multipartFile.getSize()).thenReturn(12L);
        when(multipartFile.getContentType()).thenReturn("text/plain");
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
        when(minioClient.putObject(any(PutObjectArgs.class))).thenReturn(null);

        // When
        String result = minioService.uploadFile("test-bucket", "test.txt", multipartFile);

        // Then
        assertEquals("test.txt", result);
        verify(minioClient, times(1)).putObject(any(PutObjectArgs.class));
    }

    @Test
    void testUploadFile_MultipartFile_Exception_ThrowRuntimeException() throws Exception {
        // Given
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
        when(multipartFile.getInputStream()).thenThrow(new RuntimeException("IO error"));

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            minioService.uploadFile("test-bucket", "test.txt", multipartFile);
        });

        assertTrue(exception.getMessage().contains("文件上传失败"));
    }

    // ==================== uploadFile (InputStream) 方法测试 ====================

    @Test
    void testUploadFile_InputStream_Success() throws Exception {
        // Given
        InputStream inputStream = new ByteArrayInputStream("test content".getBytes());
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
        when(minioClient.putObject(any(PutObjectArgs.class))).thenReturn(null);

        // When
        String result = minioService.uploadFile("test-bucket", "test.txt", inputStream, 12L, "text/plain");

        // Then
        assertEquals("test.txt", result);
        verify(minioClient, times(1)).putObject(any(PutObjectArgs.class));
    }

    // ==================== generatePresignedUploadUrl 方法测试 ====================

    @Test
    void testGeneratePresignedUploadUrl_Success() throws Exception {
        // Given
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
        when(publicMinioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
                .thenReturn("http://minio/upload-url");

        // When
        String result = minioService.generatePresignedUploadUrl("test-bucket", "test.txt", 30);

        // Then
        assertEquals("http://minio/upload-url", result);
        verify(publicMinioClient, times(1)).getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class));
    }

    @Test
    void testGeneratePresignedUploadUrl_Exception_ThrowRuntimeException() throws Exception {
        // Given
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
        when(publicMinioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
                .thenThrow(new RuntimeException("MinIO error"));

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            minioService.generatePresignedUploadUrl("test-bucket", "test.txt", 30);
        });

        assertTrue(exception.getMessage().contains("生成上传URL失败"));
    }

    // ==================== generatePresignedDownloadUrl 方法测试 ====================

    @Test
    void testGeneratePresignedDownloadUrl_Success() throws Exception {
        // Given
        when(publicMinioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
                .thenReturn("http://minio/download-url");

        // When
        String result = minioService.generatePresignedDownloadUrl("test-bucket", "test.txt", 60);

        // Then
        assertEquals("http://minio/download-url", result);
        verify(publicMinioClient, times(1)).getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class));
    }

    // ==================== getFileStream 方法测试 ====================

    @Test
    void testGetFileStream_Success() throws Exception {
        // Given
        when(minioClient.getObject(any(GetObjectArgs.class))).thenReturn(getObjectResponse);

        // When
        InputStream result = minioService.getFileStream("test-bucket", "test.txt");

        // Then
        assertNotNull(result);
        assertEquals(getObjectResponse, result);
        verify(minioClient, times(1)).getObject(any(GetObjectArgs.class));
    }

    @Test
    void testGetFileStream_Exception_ThrowRuntimeException() throws Exception {
        // Given
        when(minioClient.getObject(any(GetObjectArgs.class)))
                .thenThrow(new RuntimeException("MinIO error"));

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            minioService.getFileStream("test-bucket", "test.txt");
        });

        assertTrue(exception.getMessage().contains("获取文件流失败"));
    }

    // ==================== deleteFile 方法测试 ====================

    @Test
    void testDeleteFile_Success() throws Exception {
        // Given
        doNothing().when(minioClient).removeObject(any(RemoveObjectArgs.class));

        // When
        minioService.deleteFile("test-bucket", "test.txt");

        // Then
        verify(minioClient, times(1)).removeObject(any(RemoveObjectArgs.class));
    }

    @Test
    void testDeleteFile_Exception_ThrowRuntimeException() throws Exception {
        // Given
        doThrow(new RuntimeException("MinIO error")).when(minioClient).removeObject(any(RemoveObjectArgs.class));

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            minioService.deleteFile("test-bucket", "test.txt");
        });

        assertTrue(exception.getMessage().contains("文件删除失败"));
    }

    // ==================== fileExists 方法测试 ====================

    @Test
    void testFileExists_True() throws Exception {
        // Given
        when(minioClient.statObject(any(StatObjectArgs.class))).thenReturn(null);

        // When
        boolean result = minioService.fileExists("test-bucket", "test.txt");

        // Then
        assertTrue(result);
        verify(minioClient, times(1)).statObject(any(StatObjectArgs.class));
    }

    @Test
    void testFileExists_False() throws Exception {
        // Given
        when(minioClient.statObject(any(StatObjectArgs.class)))
                .thenThrow(new RuntimeException("File not found"));

        // When
        boolean result = minioService.fileExists("test-bucket", "test.txt");

        // Then
        assertFalse(result);
    }

    // ==================== generatePresignedUploadPartUrl 方法测试 ====================

    @Test
    void testGeneratePresignedUploadPartUrl_Success() throws Exception {
        // Given
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
        when(publicMinioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
                .thenReturn("http://minio/upload-part-url");

        // When
        String result = minioService.generatePresignedUploadPartUrl("test-bucket", "test.txt", 1, 30);

        // Then
        assertEquals("http://minio/upload-part-url", result);
        verify(publicMinioClient, times(1)).getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class));
    }

    // ==================== mergeChunks 方法测试 ====================

    @Test
    void testMergeChunks_Success() throws Exception {
        // Given
        when(minioClient.composeObject(any(ComposeObjectArgs.class))).thenReturn(null);
        doNothing().when(minioClient).removeObject(any(RemoveObjectArgs.class));

        // When
        String result = minioService.mergeChunks("test-bucket", "test.txt", 3);

        // Then
        assertEquals("test.txt", result);
        verify(minioClient, times(1)).composeObject(any(ComposeObjectArgs.class));
        verify(minioClient, times(3)).removeObject(any(RemoveObjectArgs.class));
    }

    @Test
    void testMergeChunks_Exception_ThrowRuntimeException() throws Exception {
        // Given
        when(minioClient.composeObject(any(ComposeObjectArgs.class)))
                .thenThrow(new RuntimeException("Compose error"));

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            minioService.mergeChunks("test-bucket", "test.txt", 3);
        });

        assertTrue(exception.getMessage().contains("合并分片失败"));
    }

    // ==================== copyFile 方法测试 ====================

    @Test
    void testCopyFile_Success() throws Exception {
        // Given
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
        when(minioClient.copyObject(any(CopyObjectArgs.class))).thenReturn(null);

        // When
        minioService.copyFile("source-bucket", "source.txt", "target-bucket", "target.txt");

        // Then
        verify(minioClient, times(1)).copyObject(any(CopyObjectArgs.class));
    }

    @Test
    void testCopyFile_Exception_ThrowRuntimeException() throws Exception {
        // Given
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
        when(minioClient.copyObject(any(CopyObjectArgs.class)))
                .thenThrow(new RuntimeException("Copy error"));

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            minioService.copyFile("source-bucket", "source.txt", "target-bucket", "target.txt");
        });

        assertTrue(exception.getMessage().contains("文件复制失败"));
    }

    // ==================== getBucketNameByFileType 方法测试 ====================

    @Test
    void testGetBucketNameByFileType_Image() {
        // Given
        when(minioConfig.getImageBucket()).thenReturn("images-bucket");

        // When
        String result = minioService.getBucketNameByFileType("image");

        // Then
        assertEquals("images-bucket", result);
    }

    @Test
    void testGetBucketNameByFileType_Video() {
        // Given
        when(minioConfig.getVideoBucket()).thenReturn("videos-bucket");

        // When
        String result = minioService.getBucketNameByFileType("video");

        // Then
        assertEquals("videos-bucket", result);
    }

    @Test
    void testGetBucketNameByFileType_Audio() {
        // Given
        when(minioConfig.getAudioBucket()).thenReturn("audios-bucket");

        // When
        String result = minioService.getBucketNameByFileType("audio");

        // Then
        assertEquals("audios-bucket", result);
    }

    @Test
    void testGetBucketNameByFileType_Document() {
        // Given
        when(minioConfig.getFileBucket()).thenReturn("files-bucket");

        // When
        String result = minioService.getBucketNameByFileType("document");

        // Then
        assertEquals("files-bucket", result);
    }

    @Test
    void testGetBucketNameByFileType_Unknown() {
        // Given
        when(minioConfig.getBucketName()).thenReturn("default-bucket");

        // When
        String result = minioService.getBucketNameByFileType("unknown");

        // Then
        assertEquals("default-bucket", result);
    }

    @Test
    void testGetBucketNameByFileType_Null() {
        // Given
        when(minioConfig.getBucketName()).thenReturn("default-bucket");

        // When
        String result = minioService.getBucketNameByFileType(null);

        // Then
        assertEquals("default-bucket", result);
    }
}