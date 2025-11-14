package com.sg.nusiss.developer.service;

import com.sg.nusiss.developer.cache.DevGameStatisticsCache;
import com.sg.nusiss.developer.entity.DevGameStatistics;
import com.sg.nusiss.developer.repository.DevGameStatisticsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * @ClassName DevGameStatisticsSyncServiceTest
 * @Author HUANG ZHENJIA
 * @Date 2025/11/14
 * @Description DevGameStatisticsSyncService 单元测试
 */
@ExtendWith(MockitoExtension.class)
class DevGameStatisticsSyncServiceTest {

    @Mock
    private DevGameStatisticsCache cache;

    @Mock
    private DevGameStatisticsRepository repository;

    @InjectMocks
    private DevGameStatisticsSyncService service;

    private DevGameStatistics existingStats;

    @BeforeEach
    void setUp() {
        existingStats = new DevGameStatistics();
        existingStats.setId("stats-1");
        existingStats.setGameId("game-1");
        existingStats.setViewCount(100);
        existingStats.setDownloadCount(50);
        existingStats.setRating(4.5);
        existingStats.setUpdatedAt(LocalDateTime.now());
    }

    // ==================== syncStatisticsFromRedis 方法测试 ====================

    @Test
    void testSyncStatisticsFromRedis_Success_InsertNewStats() {
        // Given - 新游戏，数据库中不存在
        Set<String> viewKeys = new HashSet<>(Collections.singletonList("devgame:view:game-new"));
        Set<String> downloadKeys = new HashSet<>(Collections.singletonList("devgame:download:game-new"));

        when(cache.getKeysByPrefix("devgame:view:")).thenReturn(viewKeys);
        when(cache.getKeysByPrefix("devgame:download:")).thenReturn(downloadKeys);
        when(cache.getViewCount("game-new")).thenReturn(150L);
        when(cache.getDownloadCount("game-new")).thenReturn(75L);
        when(repository.findByGameId("game-new")).thenReturn(Optional.empty());
        doNothing().when(cache).resetCounters("game-new");

        // When
        service.syncStatisticsFromRedis();

        // Then
        verify(cache, times(1)).getKeysByPrefix("devgame:view:");
        verify(cache, times(1)).getKeysByPrefix("devgame:download:");
        verify(cache, times(1)).getViewCount("game-new");
        verify(cache, times(1)).getDownloadCount("game-new");
        verify(repository, times(1)).findByGameId("game-new");
        verify(repository, times(1)).insert(any(DevGameStatistics.class));
        verify(repository, never()).updateCounts(anyString(), anyInt(), anyInt());
        verify(cache, times(1)).resetCounters("game-new");
    }

    @Test
    void testSyncStatisticsFromRedis_Success_UpdateExistingStats() {
        // Given - 游戏已存在于数据库
        Set<String> viewKeys = new HashSet<>(Collections.singletonList("devgame:view:game-1"));
        Set<String> downloadKeys = new HashSet<>(Collections.singletonList("devgame:download:game-1"));

        when(cache.getKeysByPrefix("devgame:view:")).thenReturn(viewKeys);
        when(cache.getKeysByPrefix("devgame:download:")).thenReturn(downloadKeys);
        when(cache.getViewCount("game-1")).thenReturn(200L);
        when(cache.getDownloadCount("game-1")).thenReturn(100L);
        when(repository.findByGameId("game-1")).thenReturn(Optional.of(existingStats));
        doNothing().when(cache).resetCounters("game-1");

        // When
        service.syncStatisticsFromRedis();

        // Then
        verify(repository, times(1)).findByGameId("game-1");
        verify(repository, never()).insert(any(DevGameStatistics.class));
        verify(repository, times(1)).updateCounts("game-1", 200, 100);
        verify(cache, times(1)).resetCounters("game-1");
    }

    @Test
    void testSyncStatisticsFromRedis_MultipleGames_Mixed() {
        // Given - 多个游戏，有的新增有的更新
        Set<String> viewKeys = new HashSet<>(Arrays.asList(
                "devgame:view:game-1",
                "devgame:view:game-2"
        ));
        Set<String> downloadKeys = new HashSet<>(Arrays.asList(
                "devgame:download:game-1",
                "devgame:download:game-2"
        ));

        when(cache.getKeysByPrefix("devgame:view:")).thenReturn(viewKeys);
        when(cache.getKeysByPrefix("devgame:download:")).thenReturn(downloadKeys);

        // game-1: 已存在，更新
        when(cache.getViewCount("game-1")).thenReturn(300L);
        when(cache.getDownloadCount("game-1")).thenReturn(150L);
        when(repository.findByGameId("game-1")).thenReturn(Optional.of(existingStats));
        doNothing().when(cache).resetCounters("game-1");

        // game-2: 不存在，插入
        when(cache.getViewCount("game-2")).thenReturn(100L);
        when(cache.getDownloadCount("game-2")).thenReturn(50L);
        when(repository.findByGameId("game-2")).thenReturn(Optional.empty());
        doNothing().when(cache).resetCounters("game-2");

        // When
        service.syncStatisticsFromRedis();

        // Then
        verify(repository, times(1)).updateCounts("game-1", 300, 150);
        verify(repository, times(1)).insert(any(DevGameStatistics.class));
        verify(cache, times(1)).resetCounters("game-1");
        verify(cache, times(1)).resetCounters("game-2");
    }

    @Test
    void testSyncStatisticsFromRedis_EmptyKeys_NoSync() {
        // Given - Redis中没有数据
        when(cache.getKeysByPrefix("devgame:view:")).thenReturn(Collections.emptySet());
        when(cache.getKeysByPrefix("devgame:download:")).thenReturn(Collections.emptySet());

        // When
        service.syncStatisticsFromRedis();

        // Then
        verify(cache, times(1)).getKeysByPrefix("devgame:view:");
        verify(cache, times(1)).getKeysByPrefix("devgame:download:");
        verify(cache, never()).getViewCount(anyString());
        verify(cache, never()).getDownloadCount(anyString());
        verify(repository, never()).findByGameId(anyString());
        verify(repository, never()).insert(any(DevGameStatistics.class));
        verify(repository, never()).updateCounts(anyString(), anyInt(), anyInt());
        verify(cache, never()).resetCounters(anyString());
    }

    @Test
    void testSyncStatisticsFromRedis_OnlyViewKeys() {
        // Given - 只有浏览量，没有下载量
        Set<String> viewKeys = new HashSet<>(Collections.singletonList("devgame:view:game-1"));
        Set<String> downloadKeys = Collections.emptySet();

        when(cache.getKeysByPrefix("devgame:view:")).thenReturn(viewKeys);
        when(cache.getKeysByPrefix("devgame:download:")).thenReturn(downloadKeys);
        when(cache.getViewCount("game-1")).thenReturn(100L);
        when(cache.getDownloadCount("game-1")).thenReturn(0L);
        when(repository.findByGameId("game-1")).thenReturn(Optional.empty());
        doNothing().when(cache).resetCounters("game-1");

        // When
        service.syncStatisticsFromRedis();

        // Then
        verify(cache, times(1)).getViewCount("game-1");
        verify(cache, times(1)).getDownloadCount("game-1");
        verify(repository, times(1)).insert(any(DevGameStatistics.class));
        verify(cache, times(1)).resetCounters("game-1");
    }

    @Test
    void testSyncStatisticsFromRedis_OnlyDownloadKeys() {
        // Given - 只有下载量，没有浏览量
        Set<String> viewKeys = Collections.emptySet();
        Set<String> downloadKeys = new HashSet<>(Collections.singletonList("devgame:download:game-1"));

        when(cache.getKeysByPrefix("devgame:view:")).thenReturn(viewKeys);
        when(cache.getKeysByPrefix("devgame:download:")).thenReturn(downloadKeys);
        when(cache.getViewCount("game-1")).thenReturn(0L);
        when(cache.getDownloadCount("game-1")).thenReturn(50L);
        when(repository.findByGameId("game-1")).thenReturn(Optional.empty());
        doNothing().when(cache).resetCounters("game-1");

        // When
        service.syncStatisticsFromRedis();

        // Then
        verify(cache, times(1)).getViewCount("game-1");
        verify(cache, times(1)).getDownloadCount("game-1");
        verify(repository, times(1)).insert(any(DevGameStatistics.class));
        verify(cache, times(1)).resetCounters("game-1");
    }

    @Test
    void testSyncStatisticsFromRedis_DifferentKeysForSameGame() {
        // Given - 同一个游戏在view和download中都有key
        Set<String> viewKeys = new HashSet<>(Collections.singletonList("devgame:view:game-1"));
        Set<String> downloadKeys = new HashSet<>(Collections.singletonList("devgame:download:game-1"));

        when(cache.getKeysByPrefix("devgame:view:")).thenReturn(viewKeys);
        when(cache.getKeysByPrefix("devgame:download:")).thenReturn(downloadKeys);
        when(cache.getViewCount("game-1")).thenReturn(200L);
        when(cache.getDownloadCount("game-1")).thenReturn(100L);
        when(repository.findByGameId("game-1")).thenReturn(Optional.empty());
        doNothing().when(cache).resetCounters("game-1");

        // When
        service.syncStatisticsFromRedis();

        // Then
        verify(cache, times(1)).getViewCount("game-1");
        verify(cache, times(1)).getDownloadCount("game-1");
        verify(repository, times(1)).insert(any(DevGameStatistics.class));
        verify(cache, times(1)).resetCounters("game-1");
    }

    @Test
    void testSyncStatisticsFromRedis_ExceptionDuringSync_ContinueWithOthers() {
        // Given - 一个游戏同步失败，其他应该继续
        Set<String> viewKeys = new HashSet<>(Arrays.asList(
                "devgame:view:game-error",
                "devgame:view:game-ok"
        ));
        Set<String> downloadKeys = Collections.emptySet();

        when(cache.getKeysByPrefix("devgame:view:")).thenReturn(viewKeys);
        when(cache.getKeysByPrefix("devgame:download:")).thenReturn(downloadKeys);

        // game-error: 抛出异常
        when(cache.getViewCount("game-error")).thenReturn(100L);
        when(cache.getDownloadCount("game-error")).thenReturn(50L);
        when(repository.findByGameId("game-error"))
                .thenThrow(new RuntimeException("Database error"));

        // game-ok: 正常处理
        when(cache.getViewCount("game-ok")).thenReturn(200L);
        when(cache.getDownloadCount("game-ok")).thenReturn(100L);
        when(repository.findByGameId("game-ok")).thenReturn(Optional.empty());
        doNothing().when(cache).resetCounters("game-ok");

        // When
        service.syncStatisticsFromRedis();

        // Then - game-error 失败但 game-ok 应该成功
        verify(repository, times(1)).findByGameId("game-error");
        verify(repository, times(1)).findByGameId("game-ok");
        verify(repository, times(1)).insert(any(DevGameStatistics.class));
        verify(cache, never()).resetCounters("game-error"); // 失败的不reset
        verify(cache, times(1)).resetCounters("game-ok"); // 成功的要reset
    }

    @Test
    void testSyncStatisticsFromRedis_ZeroCounts() {
        // Given - 计数为0的情况
        Set<String> viewKeys = new HashSet<>(Collections.singletonList("devgame:view:game-1"));
        Set<String> downloadKeys = Collections.emptySet();

        when(cache.getKeysByPrefix("devgame:view:")).thenReturn(viewKeys);
        when(cache.getKeysByPrefix("devgame:download:")).thenReturn(downloadKeys);
        when(cache.getViewCount("game-1")).thenReturn(0L);
        when(cache.getDownloadCount("game-1")).thenReturn(0L);
        when(repository.findByGameId("game-1")).thenReturn(Optional.empty());
        doNothing().when(cache).resetCounters("game-1");

        // When
        service.syncStatisticsFromRedis();

        // Then
        verify(repository, times(1)).insert(any(DevGameStatistics.class));
        verify(cache, times(1)).resetCounters("game-1");
    }

    @Test
    void testSyncStatisticsFromRedis_LargeCounts() {
        // Given - 大数字计数
        Set<String> viewKeys = new HashSet<>(Collections.singletonList("devgame:view:game-1"));
        Set<String> downloadKeys = Collections.emptySet();

        when(cache.getKeysByPrefix("devgame:view:")).thenReturn(viewKeys);
        when(cache.getKeysByPrefix("devgame:download:")).thenReturn(downloadKeys);
        when(cache.getViewCount("game-1")).thenReturn(999999L);
        when(cache.getDownloadCount("game-1")).thenReturn(888888L);
        when(repository.findByGameId("game-1")).thenReturn(Optional.empty());
        doNothing().when(cache).resetCounters("game-1");

        // When
        service.syncStatisticsFromRedis();

        // Then
        verify(cache, times(1)).getViewCount("game-1");
        verify(cache, times(1)).getDownloadCount("game-1");
        verify(repository, times(1)).insert(any(DevGameStatistics.class));
        verify(cache, times(1)).resetCounters("game-1");
    }

    @Test
    void testSyncStatisticsFromRedis_MultipleGamesAllNew() {
        // Given - 多个新游戏
        Set<String> viewKeys = new HashSet<>(Arrays.asList(
                "devgame:view:game-1",
                "devgame:view:game-2",
                "devgame:view:game-3"
        ));
        Set<String> downloadKeys = Collections.emptySet();

        when(cache.getKeysByPrefix("devgame:view:")).thenReturn(viewKeys);
        when(cache.getKeysByPrefix("devgame:download:")).thenReturn(downloadKeys);

        for (String gameId : Arrays.asList("game-1", "game-2", "game-3")) {
            when(cache.getViewCount(gameId)).thenReturn(100L);
            when(cache.getDownloadCount(gameId)).thenReturn(50L);
            when(repository.findByGameId(gameId)).thenReturn(Optional.empty());
            doNothing().when(cache).resetCounters(gameId);
        }


        // When
        service.syncStatisticsFromRedis();

        // Then
        verify(repository, times(3)).insert(any(DevGameStatistics.class));
        verify(cache, times(1)).resetCounters("game-1");
        verify(cache, times(1)).resetCounters("game-2");
        verify(cache, times(1)).resetCounters("game-3");
    }

    @Test
    void testSyncStatisticsFromRedis_MultipleGamesAllExisting() {
        // Given - 多个已存在的游戏
        Set<String> viewKeys = new HashSet<>(Arrays.asList(
                "devgame:view:game-1",
                "devgame:view:game-2"
        ));
        Set<String> downloadKeys = Collections.emptySet();

        when(cache.getKeysByPrefix("devgame:view:")).thenReturn(viewKeys);
        when(cache.getKeysByPrefix("devgame:download:")).thenReturn(downloadKeys);

        for (String gameId : Arrays.asList("game-1", "game-2")) {
            when(cache.getViewCount(gameId)).thenReturn(200L);
            when(cache.getDownloadCount(gameId)).thenReturn(100L);
            when(repository.findByGameId(gameId)).thenReturn(Optional.of(existingStats));
            doNothing().when(cache).resetCounters(gameId);
        }

        // When
        service.syncStatisticsFromRedis();

        // Then
        verify(repository, times(2)).updateCounts(anyString(), eq(200), eq(100));
        verify(repository, never()).insert(any(DevGameStatistics.class));
        verify(cache, times(1)).resetCounters("game-1");
        verify(cache, times(1)).resetCounters("game-2");
    }
}