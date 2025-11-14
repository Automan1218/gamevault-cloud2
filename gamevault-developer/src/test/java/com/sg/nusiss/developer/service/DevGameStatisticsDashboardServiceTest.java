package com.sg.nusiss.developer.service;

import com.sg.nusiss.developer.dto.DevDashboardDetailedResponse;
import com.sg.nusiss.developer.dto.DevGameStatsDetailDTO;
import com.sg.nusiss.developer.entity.DevGame;
import com.sg.nusiss.developer.entity.DevGameStatistics;
import com.sg.nusiss.developer.entity.DeveloperProfile;
import com.sg.nusiss.developer.repository.DevGameRepository;
import com.sg.nusiss.developer.repository.DevGameStatisticsRepository;
import com.sg.nusiss.developer.repository.DeveloperProfileRepository;
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
import static org.mockito.Mockito.*;

/**
 * @ClassName DevGameStatisticsDashboardServiceTest
 * @Author HUANG ZHENJIA
 * @Date 2025/11/14
 * @Description DevGameStatisticsDashboardService 单元测试
 */
@ExtendWith(MockitoExtension.class)
class DevGameStatisticsDashboardServiceTest {

    @Mock
    private DevGameRepository devGameRepository;

    @Mock
    private DevGameStatisticsRepository devGameStatisticsRepository;

    @Mock
    private DeveloperProfileRepository developerProfileRepository;

    @InjectMocks
    private DevGameStatisticsDashboardService service;

    private DeveloperProfile testProfile;
    private DevGame game1;
    private DevGame game2;
    private DevGame game3;
    private DevGameStatistics stats1;
    private DevGameStatistics stats2;

    @BeforeEach
    void setUp() {
        LocalDateTime now = LocalDateTime.now();

        testProfile = new DeveloperProfile();
        testProfile.setId("dev-123");
        testProfile.setUserId("user-123");
        testProfile.setProjectCount(3);

        game1 = new DevGame("game-1", "dev-123", "Game One", "Desc 1",
                now, now, now);
        game2 = new DevGame("game-2", "dev-123", "Game Two", "Desc 2",
                now, now, now);
        game3 = new DevGame("game-3", "dev-123", "Game Three", "Desc 3",
                now, now, now);

        stats1 = new DevGameStatistics();
        stats1.setGameId("game-1");
        stats1.setViewCount(100);
        stats1.setDownloadCount(50);
        stats1.setRating(4.5);
        stats1.setUpdatedAt(now);

        stats2 = new DevGameStatistics();
        stats2.setGameId("game-2");
        stats2.setViewCount(200);
        stats2.setDownloadCount(80);
        stats2.setRating(4.8);
        stats2.setUpdatedAt(now);
    }

    // ==================== getDashboardDetails 方法测试 ====================

    @Test
    void testGetDashboardDetails_Success_WithAllStats() {
        // Given
        when(devGameRepository.findByDeveloperProfileId("dev-123"))
                .thenReturn(Arrays.asList(game1, game2));
        when(devGameStatisticsRepository.findByGameId("game-1"))
                .thenReturn(Optional.of(stats1));
        when(devGameStatisticsRepository.findByGameId("game-2"))
                .thenReturn(Optional.of(stats2));

        // When
        DevDashboardDetailedResponse result = service.getDashboardDetails("dev-123");

        // Then
        assertNotNull(result);
        assertEquals("dev-123", result.getDeveloperId());

        // Verify Summary
        DevDashboardDetailedResponse.Summary summary = result.getSummary();
        assertNotNull(summary);
        assertEquals(2, summary.getTotalGames());
        assertEquals(300, summary.getTotalViews()); // 100 + 200
        assertEquals(130, summary.getTotalDownloads()); // 50 + 80
        assertEquals(4.65, summary.getAverageRating(), 0.01); // (4.5 + 4.8) / 2

        // Verify Details
        List<DevGameStatsDetailDTO> details = result.getGames();
        assertNotNull(details);
        assertEquals(2, details.size());

        DevGameStatsDetailDTO detail1 = details.get(0);
        assertEquals("game-1", detail1.getGameId());
        assertEquals("Game One", detail1.getName());
        assertEquals(100, detail1.getViewCount());
        assertEquals(50, detail1.getDownloadCount());
        assertEquals(4.5, detail1.getRating());

        DevGameStatsDetailDTO detail2 = details.get(1);
        assertEquals("game-2", detail2.getGameId());
        assertEquals("Game Two", detail2.getName());
        assertEquals(200, detail2.getViewCount());
        assertEquals(80, detail2.getDownloadCount());
        assertEquals(4.8, detail2.getRating());

        verify(devGameRepository, times(1)).findByDeveloperProfileId("dev-123");
        verify(devGameStatisticsRepository, times(1)).findByGameId("game-1");
        verify(devGameStatisticsRepository, times(1)).findByGameId("game-2");
    }

    @Test
    void testGetDashboardDetails_Success_WithPartialStats() {
        // Given - game2 没有统计数据
        when(devGameRepository.findByDeveloperProfileId("dev-123"))
                .thenReturn(Arrays.asList(game1, game2));
        when(devGameStatisticsRepository.findByGameId("game-1"))
                .thenReturn(Optional.of(stats1));
        when(devGameStatisticsRepository.findByGameId("game-2"))
                .thenReturn(Optional.empty());

        // When
        DevDashboardDetailedResponse result = service.getDashboardDetails("dev-123");

        // Then
        assertNotNull(result);

        DevDashboardDetailedResponse.Summary summary = result.getSummary();
        assertEquals(2, summary.getTotalGames());
        assertEquals(100, summary.getTotalViews()); // 只有 game-1
        assertEquals(50, summary.getTotalDownloads());
        assertEquals(4.5, summary.getAverageRating()); // 只计算有评分的游戏

        List<DevGameStatsDetailDTO> details = result.getGames();
        assertEquals(2, details.size());

        // game-2 应该有默认值
        DevGameStatsDetailDTO detail2 = details.get(1);
        assertEquals("game-2", detail2.getGameId());
        assertEquals(0, detail2.getViewCount());
        assertEquals(0, detail2.getDownloadCount());
        assertEquals(0.0, detail2.getRating());
    }

    @Test
    void testGetDashboardDetails_Success_NoStats() {
        // Given - 所有游戏都没有统计数据
        when(devGameRepository.findByDeveloperProfileId("dev-123"))
                .thenReturn(Arrays.asList(game1, game2));
        when(devGameStatisticsRepository.findByGameId("game-1"))
                .thenReturn(Optional.empty());
        when(devGameStatisticsRepository.findByGameId("game-2"))
                .thenReturn(Optional.empty());

        // When
        DevDashboardDetailedResponse result = service.getDashboardDetails("dev-123");

        // Then
        assertNotNull(result);

        DevDashboardDetailedResponse.Summary summary = result.getSummary();
        assertEquals(2, summary.getTotalGames());
        assertEquals(0, summary.getTotalViews());
        assertEquals(0, summary.getTotalDownloads());
        assertEquals(0.0, summary.getAverageRating());

        List<DevGameStatsDetailDTO> details = result.getGames();
        assertEquals(2, details.size());

        details.forEach(detail -> {
            assertEquals(0, detail.getViewCount());
            assertEquals(0, detail.getDownloadCount());
            assertEquals(0.0, detail.getRating());
        });
    }

    @Test
    void testGetDashboardDetails_NoGames() {
        // Given
        when(devGameRepository.findByDeveloperProfileId("dev-123"))
                .thenReturn(Collections.emptyList());

        // When
        DevDashboardDetailedResponse result = service.getDashboardDetails("dev-123");

        // Then
        assertNotNull(result);
        assertEquals("dev-123", result.getDeveloperId());

        DevDashboardDetailedResponse.Summary summary = result.getSummary();
        assertEquals(0, summary.getTotalGames());
        assertEquals(0, summary.getTotalViews());
        assertEquals(0, summary.getTotalDownloads());
        assertEquals(0.0, summary.getAverageRating());

        List<DevGameStatsDetailDTO> details = result.getGames();
        assertNotNull(details);
        assertTrue(details.isEmpty());

        verify(devGameStatisticsRepository, never()).findByGameId(anyString());
    }

    @Test
    void testGetDashboardDetails_MixedRatings() {
        // Given - 测试平均分计算（有些游戏评分为0）
        DevGameStatistics stats3 = new DevGameStatistics();
        stats3.setGameId("game-3");
        stats3.setViewCount(50);
        stats3.setDownloadCount(20);
        stats3.setRating(0.0); // 无评分
        stats3.setUpdatedAt(LocalDateTime.now());

        when(devGameRepository.findByDeveloperProfileId("dev-123"))
                .thenReturn(Arrays.asList(game1, game2, game3));
        when(devGameStatisticsRepository.findByGameId("game-1"))
                .thenReturn(Optional.of(stats1));
        when(devGameStatisticsRepository.findByGameId("game-2"))
                .thenReturn(Optional.of(stats2));
        when(devGameStatisticsRepository.findByGameId("game-3"))
                .thenReturn(Optional.of(stats3));

        // When
        DevDashboardDetailedResponse result = service.getDashboardDetails("dev-123");

        // Then
        DevDashboardDetailedResponse.Summary summary = result.getSummary();
        assertEquals(3, summary.getTotalGames());
        assertEquals(350, summary.getTotalViews()); // 100 + 200 + 50
        assertEquals(150, summary.getTotalDownloads()); // 50 + 80 + 20
        // 平均分只计算有评分的: (4.5 + 4.8) / 2 = 4.65
        assertEquals(4.65, summary.getAverageRating(), 0.01);
    }

    // ==================== getDashboardByUserId 方法测试 ====================

    @Test
    void testGetDashboardByUserId_DeveloperExists() {
        // Given
        when(developerProfileRepository.findByUserId("user-123"))
                .thenReturn(Optional.of(testProfile));
        when(devGameRepository.findByDeveloperProfileId("dev-123"))
                .thenReturn(Arrays.asList(game1, game2));
        when(devGameStatisticsRepository.findByGameId("game-1"))
                .thenReturn(Optional.of(stats1));
        when(devGameStatisticsRepository.findByGameId("game-2"))
                .thenReturn(Optional.of(stats2));

        // When
        DevDashboardDetailedResponse result = service.getDashboardByUserId("user-123");

        // Then
        assertNotNull(result);
        assertEquals("dev-123", result.getDeveloperId());

        DevDashboardDetailedResponse.Summary summary = result.getSummary();
        assertEquals(2, summary.getTotalGames());
        assertEquals(300, summary.getTotalViews());
        assertEquals(130, summary.getTotalDownloads());

        verify(developerProfileRepository, times(1)).findByUserId("user-123");
        verify(devGameRepository, times(1)).findByDeveloperProfileId("dev-123");
    }

    @Test
    void testGetDashboardByUserId_DeveloperNotExists_ReturnEmpty() {
        // Given
        when(developerProfileRepository.findByUserId("user-999"))
                .thenReturn(Optional.empty());

        // When
        DevDashboardDetailedResponse result = service.getDashboardByUserId("user-999");

        // Then
        assertNotNull(result);
        assertEquals("user-999", result.getDeveloperId()); // 使用 userId

        DevDashboardDetailedResponse.Summary summary = result.getSummary();
        assertEquals(0, summary.getTotalGames());
        assertEquals(0, summary.getTotalViews());
        assertEquals(0, summary.getTotalDownloads());
        assertEquals(0.0, summary.getAverageRating());

        List<DevGameStatsDetailDTO> details = result.getGames();
        assertNotNull(details);
        assertTrue(details.isEmpty());

        verify(developerProfileRepository, times(1)).findByUserId("user-999");
        verify(devGameRepository, never()).findByDeveloperProfileId(anyString());
    }

    @Test
    void testGetDashboardByUserId_DeveloperExists_NoGames() {
        // Given
        when(developerProfileRepository.findByUserId("user-123"))
                .thenReturn(Optional.of(testProfile));
        when(devGameRepository.findByDeveloperProfileId("dev-123"))
                .thenReturn(Collections.emptyList());

        // When
        DevDashboardDetailedResponse result = service.getDashboardByUserId("user-123");

        // Then
        assertNotNull(result);
        assertEquals("dev-123", result.getDeveloperId());

        DevDashboardDetailedResponse.Summary summary = result.getSummary();
        assertEquals(0, summary.getTotalGames());
        assertEquals(0, summary.getTotalViews());
        assertEquals(0, summary.getTotalDownloads());
        assertEquals(0.0, summary.getAverageRating());

        assertTrue(result.getGames().isEmpty());
    }

    // ==================== 边界和特殊场景测试 ====================

    @Test
    void testGetDashboardDetails_SingleGame() {
        // Given
        when(devGameRepository.findByDeveloperProfileId("dev-123"))
                .thenReturn(Collections.singletonList(game1));
        when(devGameStatisticsRepository.findByGameId("game-1"))
                .thenReturn(Optional.of(stats1));

        // When
        DevDashboardDetailedResponse result = service.getDashboardDetails("dev-123");

        // Then
        DevDashboardDetailedResponse.Summary summary = result.getSummary();
        assertEquals(1, summary.getTotalGames());
        assertEquals(100, summary.getTotalViews());
        assertEquals(50, summary.getTotalDownloads());
        assertEquals(4.5, summary.getAverageRating());

        assertEquals(1, result.getGames().size());
    }

    @Test
    void testGetDashboardDetails_AllGamesZeroRating() {
        // Given - 所有游戏评分都为0
        stats1.setRating(0.0);
        stats2.setRating(0.0);

        when(devGameRepository.findByDeveloperProfileId("dev-123"))
                .thenReturn(Arrays.asList(game1, game2));
        when(devGameStatisticsRepository.findByGameId("game-1"))
                .thenReturn(Optional.of(stats1));
        when(devGameStatisticsRepository.findByGameId("game-2"))
                .thenReturn(Optional.of(stats2));

        // When
        DevDashboardDetailedResponse result = service.getDashboardDetails("dev-123");

        // Then
        DevDashboardDetailedResponse.Summary summary = result.getSummary();
        assertEquals(0.0, summary.getAverageRating()); // 平均分应该为0
    }

    @Test
    void testGetDashboardDetails_LargeNumbers() {
        // Given - 测试大数字
        stats1.setViewCount(1000000);
        stats1.setDownloadCount(500000);
        stats2.setViewCount(2000000);
        stats2.setDownloadCount(800000);

        when(devGameRepository.findByDeveloperProfileId("dev-123"))
                .thenReturn(Arrays.asList(game1, game2));
        when(devGameStatisticsRepository.findByGameId("game-1"))
                .thenReturn(Optional.of(stats1));
        when(devGameStatisticsRepository.findByGameId("game-2"))
                .thenReturn(Optional.of(stats2));

        // When
        DevDashboardDetailedResponse result = service.getDashboardDetails("dev-123");

        // Then
        DevDashboardDetailedResponse.Summary summary = result.getSummary();
        assertEquals(3000000, summary.getTotalViews());
        assertEquals(1300000, summary.getTotalDownloads());
    }
}
