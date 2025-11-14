package com.sg.nusiss.social.service;

import com.sg.nusiss.social.entity.file.FileAccessLog;
import com.sg.nusiss.social.repository.file.FileAccessLogRepository;
import com.sg.nusiss.social.service.file.FileAccessLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * @ClassName FileAccessLogServiceTest
 * @Author HUANG ZHENJIA
 * @Date 2025/11/14
 * @Description FileAccessLogService 单元测试
 */
@ExtendWith(MockitoExtension.class)
class FileAccessLogServiceTest {

    @Mock
    private FileAccessLogRepository accessLogRepository;

    @InjectMocks
    private FileAccessLogService fileAccessLogService;

    private FileAccessLog testLog;
    private LocalDateTime now;

    @BeforeEach
    void setUp() {
        now = LocalDateTime.now();

        testLog = FileAccessLog.builder()
                .id(1L)
                .fileId("file123")
                .userId(1L)
                .accessType(1)
                .ipAddress("192.168.1.1")
                .userAgent("Mozilla/5.0")
                .createdAt(now)
                .build();
    }

    // ==================== recordAccessLog 方法测试 ====================

    @Test
    void testRecordAccessLog_Success() {
        // Given
        when(accessLogRepository.save(any(FileAccessLog.class))).thenReturn(testLog);

        // When
        fileAccessLogService.recordAccessLog("file123", 1L, 1, "192.168.1.1", "Mozilla/5.0");

        // Then
        verify(accessLogRepository, times(1)).save(any(FileAccessLog.class));
    }

    @Test
    void testRecordAccessLog_Exception_NoThrow() {
        // Given
        when(accessLogRepository.save(any(FileAccessLog.class)))
                .thenThrow(new RuntimeException("Database error"));

        // When & Then
        assertDoesNotThrow(() -> {
            fileAccessLogService.recordAccessLog("file123", 1L, 1, "192.168.1.1", "Mozilla/5.0");
        });

        verify(accessLogRepository, times(1)).save(any(FileAccessLog.class));
    }

    // ==================== getFileAccessLogs 方法测试 ====================

    @Test
    void testGetFileAccessLogs_Success() {
        // Given
        FileAccessLog log1 = FileAccessLog.builder().fileId("file123").userId(1L).build();
        FileAccessLog log2 = FileAccessLog.builder().fileId("file123").userId(2L).build();

        when(accessLogRepository.findByFileIdOrderByCreatedAtDesc("file123"))
                .thenReturn(Arrays.asList(log1, log2));

        // When
        List<FileAccessLog> result = fileAccessLogService.getFileAccessLogs("file123");

        // Then
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("file123", result.get(0).getFileId());

        verify(accessLogRepository, times(1)).findByFileIdOrderByCreatedAtDesc("file123");
    }

    @Test
    void testGetFileAccessLogs_EmptyResult() {
        // Given
        when(accessLogRepository.findByFileIdOrderByCreatedAtDesc("file123"))
                .thenReturn(new ArrayList<>());

        // When
        List<FileAccessLog> result = fileAccessLogService.getFileAccessLogs("file123");

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // ==================== getUserAccessLogs 方法测试 ====================

    @Test
    void testGetUserAccessLogs_Success() {
        // Given
        FileAccessLog log1 = FileAccessLog.builder().userId(1L).fileId("file1").build();
        FileAccessLog log2 = FileAccessLog.builder().userId(1L).fileId("file2").build();

        when(accessLogRepository.findByUserIdOrderByCreatedAtDesc(1L))
                .thenReturn(Arrays.asList(log1, log2));

        // When
        List<FileAccessLog> result = fileAccessLogService.getUserAccessLogs(1L);

        // Then
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals(1L, result.get(0).getUserId());

        verify(accessLogRepository, times(1)).findByUserIdOrderByCreatedAtDesc(1L);
    }

    // ==================== getFileAccessCount 方法测试 ====================

    @Test
    void testGetFileAccessCount_Success() {
        // Given
        when(accessLogRepository.countByFileId("file123")).thenReturn(10L);

        // When
        Long result = fileAccessLogService.getFileAccessCount("file123");

        // Then
        assertEquals(10L, result);
        verify(accessLogRepository, times(1)).countByFileId("file123");
    }

    // ==================== getFileAccessCountByTimeRange 方法测试 ====================

    @Test
    void testGetFileAccessCountByTimeRange_Success() {
        // Given
        LocalDateTime start = now.minusDays(7);
        LocalDateTime end = now;

        when(accessLogRepository.countByFileIdAndCreatedAtBetween("file123", start, end))
                .thenReturn(5L);

        // When
        Long result = fileAccessLogService.getFileAccessCountByTimeRange("file123", start, end);

        // Then
        assertEquals(5L, result);
        verify(accessLogRepository, times(1))
                .countByFileIdAndCreatedAtBetween("file123", start, end);
    }

    // ==================== getFileDownloadCount 方法测试 ====================

    @Test
    void testGetFileDownloadCount_Success() {
        // Given
        when(accessLogRepository.countByFileIdAndAccessType("file123", 2)).thenReturn(3L);

        // When
        Long result = fileAccessLogService.getFileDownloadCount("file123");

        // Then
        assertEquals(3L, result);
        verify(accessLogRepository, times(1)).countByFileIdAndAccessType("file123", 2);
    }

    // ==================== getPopularFiles 方法测试 ====================

    @Test
    void testGetPopularFiles_Success() {
        // Given
        List<Object[]> mockResults = new ArrayList<>();
        mockResults.add(new Object[]{"file1", 100L});
        mockResults.add(new Object[]{"file2", 80L});

        when(accessLogRepository.findPopularFiles(any(LocalDateTime.class)))
                .thenReturn(mockResults);

        // When
        List<FileAccessLogService.PopularFileStats> result = fileAccessLogService.getPopularFiles(7);

        // Then
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("file1", result.get(0).getFileId());
        assertEquals(100L, result.get(0).getAccessCount());
        assertEquals("file2", result.get(1).getFileId());
        assertEquals(80L, result.get(1).getAccessCount());

        verify(accessLogRepository, times(1)).findPopularFiles(any(LocalDateTime.class));
    }

    @Test
    void testGetPopularFiles_EmptyResult() {
        // Given
        when(accessLogRepository.findPopularFiles(any(LocalDateTime.class)))
                .thenReturn(new ArrayList<>());

        // When
        List<FileAccessLogService.PopularFileStats> result = fileAccessLogService.getPopularFiles(7);

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // ==================== getFileAccessStatsByType 方法测试 ====================

    @Test
    void testGetFileAccessStatsByType_Success() {
        // Given
        List<Object[]> mockResults = new ArrayList<>();
        mockResults.add(new Object[]{1, 50L});  // 查看: 50次
        mockResults.add(new Object[]{2, 30L});  // 下载: 30次
        mockResults.add(new Object[]{3, 10L});  // 分享: 10次

        when(accessLogRepository.countAccessTypesByFileId("file123"))
                .thenReturn(mockResults);

        // When
        Map<String, Long> result = fileAccessLogService.getFileAccessStatsByType("file123");

        // Then
        assertNotNull(result);
        assertEquals(3, result.size());
        assertEquals(50L, result.get("查看"));
        assertEquals(30L, result.get("下载"));
        assertEquals(10L, result.get("分享"));

        verify(accessLogRepository, times(1)).countAccessTypesByFileId("file123");
    }

    @Test
    void testGetFileAccessStatsByType_UnknownType() {
        // Given
        List<Object[]> mockResults = new ArrayList<>();
        mockResults.add(new Object[]{99, 5L});  // 未知类型

        when(accessLogRepository.countAccessTypesByFileId("file123"))
                .thenReturn(mockResults);

        // When
        Map<String, Long> result = fileAccessLogService.getFileAccessStatsByType("file123");

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(5L, result.get("未知"));
    }

    // ==================== getUserAccessCount 方法测试 ====================

    @Test
    void testGetUserAccessCount_Success() {
        // Given
        LocalDateTime start = now.minusDays(30);
        LocalDateTime end = now;

        when(accessLogRepository.countByUserIdAndCreatedAtBetween(1L, start, end))
                .thenReturn(15L);

        // When
        Long result = fileAccessLogService.getUserAccessCount(1L, start, end);

        // Then
        assertEquals(15L, result);
        verify(accessLogRepository, times(1))
                .countByUserIdAndCreatedAtBetween(1L, start, end);
    }

    // ==================== cleanupOldLogs 方法测试 ====================

    @Test
    void testCleanupOldLogs_Success() {
        // Given
        int daysToKeep = 90;
        doNothing().when(accessLogRepository).deleteByCreatedAtBefore(any(LocalDateTime.class));

        // When
        fileAccessLogService.cleanupOldLogs(daysToKeep);

        // Then
        verify(accessLogRepository, times(1)).deleteByCreatedAtBefore(any(LocalDateTime.class));
    }

    // ==================== getAccessLogsByIp 方法测试 ====================

    @Test
    void testGetAccessLogsByIp_Success() {
        // Given
        String ipAddress = "192.168.1.1";
        FileAccessLog log1 = FileAccessLog.builder().ipAddress(ipAddress).build();
        FileAccessLog log2 = FileAccessLog.builder().ipAddress(ipAddress).build();

        when(accessLogRepository.findByIpAddressOrderByCreatedAtDesc(ipAddress))
                .thenReturn(Arrays.asList(log1, log2));

        // When
        List<FileAccessLog> result = fileAccessLogService.getAccessLogsByIp(ipAddress);

        // Then
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals(ipAddress, result.get(0).getIpAddress());

        verify(accessLogRepository, times(1)).findByIpAddressOrderByCreatedAtDesc(ipAddress);
    }

    // ==================== getAccessLogsByTimeRange 方法测试 ====================

    @Test
    void testGetAccessLogsByTimeRange_Success() {
        // Given
        LocalDateTime start = now.minusDays(7);
        LocalDateTime end = now;

        FileAccessLog log1 = FileAccessLog.builder().createdAt(now.minusDays(3)).build();
        FileAccessLog log2 = FileAccessLog.builder().createdAt(now.minusDays(5)).build();

        when(accessLogRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(start, end))
                .thenReturn(Arrays.asList(log1, log2));

        // When
        List<FileAccessLog> result = fileAccessLogService.getAccessLogsByTimeRange(start, end);

        // Then
        assertNotNull(result);
        assertEquals(2, result.size());

        verify(accessLogRepository, times(1))
                .findByCreatedAtBetweenOrderByCreatedAtDesc(start, end);
    }

    // ==================== PopularFileStats 类测试 ====================

    @Test
    void testPopularFileStats_Constructor() {
        // When
        FileAccessLogService.PopularFileStats stats =
                new FileAccessLogService.PopularFileStats("file123", 100L);

        // Then
        assertEquals("file123", stats.getFileId());
        assertEquals(100L, stats.getAccessCount());
    }

    @Test
    void testPopularFileStats_NoArgsConstructor() {
        // When
        FileAccessLogService.PopularFileStats stats =
                new FileAccessLogService.PopularFileStats();
        stats.setFileId("file456");
        stats.setAccessCount(200L);

        // Then
        assertEquals("file456", stats.getFileId());
        assertEquals(200L, stats.getAccessCount());
    }
}