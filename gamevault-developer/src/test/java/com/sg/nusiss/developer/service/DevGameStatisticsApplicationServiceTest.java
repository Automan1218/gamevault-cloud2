package com.sg.nusiss.developer.service;

import com.sg.nusiss.developer.cache.DevGameStatisticsCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DevGameStatisticsApplicationServiceTest {

    @Mock
    private DevGameStatisticsCache statisticsCache;

    @InjectMocks
    private DevGameStatisticsApplicationService service;

    private static final String TEST_GAME_ID = "game-123";

    @BeforeEach
    void setUp() {
        // 可以在这里初始化一些通用的测试数据
    }

    // ==================== recordGameView 方法测试 ====================

    @Test
    void testRecordGameView_Success() {
        // Given
        doNothing().when(statisticsCache).incrementView(TEST_GAME_ID);

        // When
        service.recordGameView(TEST_GAME_ID);

        // Then
        verify(statisticsCache, times(1)).incrementView(TEST_GAME_ID);
    }

    @Test
    void testRecordGameView_MultipleViews() {
        // Given
        doNothing().when(statisticsCache).incrementView(TEST_GAME_ID);

        // When
        service.recordGameView(TEST_GAME_ID);
        service.recordGameView(TEST_GAME_ID);
        service.recordGameView(TEST_GAME_ID);

        // Then
        verify(statisticsCache, times(3)).incrementView(TEST_GAME_ID);
    }

    @Test
    void testRecordGameView_DifferentGames() {
        // Given
        String gameId1 = "game-1";
        String gameId2 = "game-2";
        doNothing().when(statisticsCache).incrementView(anyString());

        // When
        service.recordGameView(gameId1);
        service.recordGameView(gameId2);

        // Then
        verify(statisticsCache, times(1)).incrementView(gameId1);
        verify(statisticsCache, times(1)).incrementView(gameId2);
    }

    @Test
    void testRecordGameView_CacheException_ShouldPropagate() {
        // Given
        doThrow(new RuntimeException("Cache error")).when(statisticsCache).incrementView(TEST_GAME_ID);

        // When & Then
        assertThrows(RuntimeException.class, () -> {
            service.recordGameView(TEST_GAME_ID);
        });

        verify(statisticsCache, times(1)).incrementView(TEST_GAME_ID);
    }

    // ==================== recordGameDownload 方法测试 ====================

    @Test
    void testRecordGameDownload_Success() {
        // Given
        doNothing().when(statisticsCache).incrementDownload(TEST_GAME_ID);

        // When
        service.recordGameDownload(TEST_GAME_ID);

        // Then
        verify(statisticsCache, times(1)).incrementDownload(TEST_GAME_ID);
    }

    @Test
    void testRecordGameDownload_MultipleDownloads() {
        // Given
        doNothing().when(statisticsCache).incrementDownload(TEST_GAME_ID);

        // When
        service.recordGameDownload(TEST_GAME_ID);
        service.recordGameDownload(TEST_GAME_ID);

        // Then
        verify(statisticsCache, times(2)).incrementDownload(TEST_GAME_ID);
    }

    @Test
    void testRecordGameDownload_DifferentGames() {
        // Given
        String gameId1 = "game-1";
        String gameId2 = "game-2";
        doNothing().when(statisticsCache).incrementDownload(anyString());

        // When
        service.recordGameDownload(gameId1);
        service.recordGameDownload(gameId2);

        // Then
        verify(statisticsCache, times(1)).incrementDownload(gameId1);
        verify(statisticsCache, times(1)).incrementDownload(gameId2);
    }

    @Test
    void testRecordGameDownload_CacheException_ShouldPropagate() {
        // Given
        doThrow(new RuntimeException("Cache error")).when(statisticsCache).incrementDownload(TEST_GAME_ID);

        // When & Then
        assertThrows(RuntimeException.class, () -> {
            service.recordGameDownload(TEST_GAME_ID);
        });

        verify(statisticsCache, times(1)).incrementDownload(TEST_GAME_ID);
    }

    // ==================== getViewCount 方法测试 ====================

    @Test
    void testGetViewCount_Success() {
        // Given
        when(statisticsCache.getViewCount(TEST_GAME_ID)).thenReturn(100L);

        // When
        long result = service.getViewCount(TEST_GAME_ID);

        // Then
        assertEquals(100L, result);
        verify(statisticsCache, times(1)).getViewCount(TEST_GAME_ID);
    }

    @Test
    void testGetViewCount_ZeroCount() {
        // Given
        when(statisticsCache.getViewCount(TEST_GAME_ID)).thenReturn(0L);

        // When
        long result = service.getViewCount(TEST_GAME_ID);

        // Then
        assertEquals(0L, result);
        verify(statisticsCache, times(1)).getViewCount(TEST_GAME_ID);
    }

    @Test
    void testGetViewCount_LargeNumber() {
        // Given
        long largeCount = 9999999L;
        when(statisticsCache.getViewCount(TEST_GAME_ID)).thenReturn(largeCount);

        // When
        long result = service.getViewCount(TEST_GAME_ID);

        // Then
        assertEquals(largeCount, result);
    }

    @Test
    void testGetViewCount_DifferentGames() {
        // Given
        String gameId1 = "game-1";
        String gameId2 = "game-2";
        when(statisticsCache.getViewCount(gameId1)).thenReturn(100L);
        when(statisticsCache.getViewCount(gameId2)).thenReturn(200L);

        // When
        long result1 = service.getViewCount(gameId1);
        long result2 = service.getViewCount(gameId2);

        // Then
        assertEquals(100L, result1);
        assertEquals(200L, result2);
    }

    @Test
    void testGetViewCount_CacheException_ShouldPropagate() {
        // Given
        when(statisticsCache.getViewCount(TEST_GAME_ID))
                .thenThrow(new RuntimeException("Cache error"));

        // When & Then
        assertThrows(RuntimeException.class, () -> {
            service.getViewCount(TEST_GAME_ID);
        });
    }

    // ==================== getDownloadCount 方法测试 ====================

    @Test
    void testGetDownloadCount_Success() {
        // Given
        when(statisticsCache.getDownloadCount(TEST_GAME_ID)).thenReturn(50L);

        // When
        long result = service.getDownloadCount(TEST_GAME_ID);

        // Then
        assertEquals(50L, result);
        verify(statisticsCache, times(1)).getDownloadCount(TEST_GAME_ID);
    }

    @Test
    void testGetDownloadCount_ZeroCount() {
        // Given
        when(statisticsCache.getDownloadCount(TEST_GAME_ID)).thenReturn(0L);

        // When
        long result = service.getDownloadCount(TEST_GAME_ID);

        // Then
        assertEquals(0L, result);
        verify(statisticsCache, times(1)).getDownloadCount(TEST_GAME_ID);
    }

    @Test
    void testGetDownloadCount_LargeNumber() {
        // Given
        long largeCount = 8888888L;
        when(statisticsCache.getDownloadCount(TEST_GAME_ID)).thenReturn(largeCount);

        // When
        long result = service.getDownloadCount(TEST_GAME_ID);

        // Then
        assertEquals(largeCount, result);
    }

    @Test
    void testGetDownloadCount_DifferentGames() {
        // Given
        String gameId1 = "game-1";
        String gameId2 = "game-2";
        when(statisticsCache.getDownloadCount(gameId1)).thenReturn(30L);
        when(statisticsCache.getDownloadCount(gameId2)).thenReturn(60L);

        // When
        long result1 = service.getDownloadCount(gameId1);
        long result2 = service.getDownloadCount(gameId2);

        // Then
        assertEquals(30L, result1);
        assertEquals(60L, result2);
    }

    @Test
    void testGetDownloadCount_CacheException_ShouldPropagate() {
        // Given
        when(statisticsCache.getDownloadCount(TEST_GAME_ID))
                .thenThrow(new RuntimeException("Cache error"));

        // When & Then
        assertThrows(RuntimeException.class, () -> {
            service.getDownloadCount(TEST_GAME_ID);
        });
    }

    // ==================== 综合场景测试 ====================

    @Test
    void testRecordAndQuery_ViewCount() {
        // Given
        doNothing().when(statisticsCache).incrementView(TEST_GAME_ID);
        when(statisticsCache.getViewCount(TEST_GAME_ID)).thenReturn(1L, 2L, 3L);

        // When & Then
        service.recordGameView(TEST_GAME_ID);
        assertEquals(1L, service.getViewCount(TEST_GAME_ID));

        service.recordGameView(TEST_GAME_ID);
        assertEquals(2L, service.getViewCount(TEST_GAME_ID));

        service.recordGameView(TEST_GAME_ID);
        assertEquals(3L, service.getViewCount(TEST_GAME_ID));

        verify(statisticsCache, times(3)).incrementView(TEST_GAME_ID);
        verify(statisticsCache, times(3)).getViewCount(TEST_GAME_ID);
    }

    @Test
    void testRecordAndQuery_DownloadCount() {
        // Given
        doNothing().when(statisticsCache).incrementDownload(TEST_GAME_ID);
        when(statisticsCache.getDownloadCount(TEST_GAME_ID)).thenReturn(1L, 2L);

        // When & Then
        service.recordGameDownload(TEST_GAME_ID);
        assertEquals(1L, service.getDownloadCount(TEST_GAME_ID));

        service.recordGameDownload(TEST_GAME_ID);
        assertEquals(2L, service.getDownloadCount(TEST_GAME_ID));

        verify(statisticsCache, times(2)).incrementDownload(TEST_GAME_ID);
        verify(statisticsCache, times(2)).getDownloadCount(TEST_GAME_ID);
    }

    @Test
    void testMixedOperations() {
        // Given
        doNothing().when(statisticsCache).incrementView(TEST_GAME_ID);
        doNothing().when(statisticsCache).incrementDownload(TEST_GAME_ID);
        when(statisticsCache.getViewCount(TEST_GAME_ID)).thenReturn(5L);
        when(statisticsCache.getDownloadCount(TEST_GAME_ID)).thenReturn(3L);

        // When
        service.recordGameView(TEST_GAME_ID);
        service.recordGameView(TEST_GAME_ID);
        service.recordGameDownload(TEST_GAME_ID);

        long viewCount = service.getViewCount(TEST_GAME_ID);
        long downloadCount = service.getDownloadCount(TEST_GAME_ID);

        // Then
        assertEquals(5L, viewCount);
        assertEquals(3L, downloadCount);

        verify(statisticsCache, times(2)).incrementView(TEST_GAME_ID);
        verify(statisticsCache, times(1)).incrementDownload(TEST_GAME_ID);
        verify(statisticsCache, times(1)).getViewCount(TEST_GAME_ID);
        verify(statisticsCache, times(1)).getDownloadCount(TEST_GAME_ID);
    }

    @Test
    void testMultipleGames_IndependentStatistics() {
        // Given
        String game1 = "game-1";
        String game2 = "game-2";

        doNothing().when(statisticsCache).incrementView(anyString());
        doNothing().when(statisticsCache).incrementDownload(anyString());

        when(statisticsCache.getViewCount(game1)).thenReturn(10L);
        when(statisticsCache.getViewCount(game2)).thenReturn(20L);
        when(statisticsCache.getDownloadCount(game1)).thenReturn(5L);
        when(statisticsCache.getDownloadCount(game2)).thenReturn(15L);

        // When
        service.recordGameView(game1);
        service.recordGameView(game2);
        service.recordGameView(game2);

        service.recordGameDownload(game1);
        service.recordGameDownload(game2);

        // Then
        assertEquals(10L, service.getViewCount(game1));
        assertEquals(20L, service.getViewCount(game2));
        assertEquals(5L, service.getDownloadCount(game1));
        assertEquals(15L, service.getDownloadCount(game2));

        verify(statisticsCache, times(1)).incrementView(game1);
        verify(statisticsCache, times(2)).incrementView(game2);
        verify(statisticsCache, times(1)).incrementDownload(game1);
        verify(statisticsCache, times(1)).incrementDownload(game2);
    }
}