package com.sg.nusiss.developer.service;

import com.sg.nusiss.developer.cache.DevGameStatisticsCache;
import com.sg.nusiss.developer.dto.HotGameResponse;
import com.sg.nusiss.developer.entity.DevGame;
import com.sg.nusiss.developer.entity.DevGameAsset;
import com.sg.nusiss.developer.repository.DevGameAssetRepository;
import com.sg.nusiss.developer.repository.DevGameRepository;
import com.sg.nusiss.developer.util.AssetUrlBuilder;
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
 * @ClassName DevGameStatisticsQueryServiceTest
 * @Author HUANG ZHENJIA
 * @Date 2025/11/14
 * @Description DevGameStatisticsQueryService 单元测试
 */
@ExtendWith(MockitoExtension.class)
class DevGameStatisticsQueryServiceTest {

    @Mock
    private DevGameStatisticsCache cache;

    @Mock
    private DevGameRepository devGameRepository;

    @Mock
    private DevGameAssetRepository devGameAssetRepository;

    @Mock
    private AssetUrlBuilder assetUrlBuilder;

    @InjectMocks
    private DevGameStatisticsQueryService service;

    private DevGame game1;
    private DevGame game2;
    private DevGame game3;
    private DevGameAsset imageAsset1;
    private DevGameAsset imageAsset2;

    @BeforeEach
    void setUp() {
        LocalDateTime now = LocalDateTime.now();

        game1 = new DevGame("game-1", "dev-1", "Hot Game One", "Description 1",
                now, now, now);
        game2 = new DevGame("game-2", "dev-1", "Hot Game Two", "Description 2",
                now, now, now);
        game3 = new DevGame("game-3", "dev-1", "Hot Game Three", "Description 3",
                now, now, now);

        imageAsset1 = new DevGameAsset("asset-1", "game-1", "image",
                "cover1.jpg", "/path/cover1.jpg", 1024L, "image/jpeg", now);
        imageAsset2 = new DevGameAsset("asset-2", "game-2", "image",
                "cover2.jpg", "/path/cover2.jpg", 1024L, "image/jpeg", now);
    }

    // ==================== getHotGames 方法测试 ====================

    @Test
    void testGetHotGames_Success_WithMultipleGames() {
        // Given
        Set<String> viewKeys = new LinkedHashSet<>(Arrays.asList(
                "devgame:view:game-1",
                "devgame:view:game-2",
                "devgame:view:game-3"
        ));

        when(cache.getKeysByPrefix("devgame:view:")).thenReturn(viewKeys);

        // game-1: 1000 views, 100 downloads -> score = 1000*0.7 + 100*1.3 = 830
        when(cache.getViewCount("game-1")).thenReturn(1000L);
        when(cache.getDownloadCount("game-1")).thenReturn(100L);
        when(devGameRepository.findById("game-1")).thenReturn(Optional.of(game1));
        when(devGameAssetRepository.findFirstByGameIdAndType("game-1", "image"))
                .thenReturn(Optional.of(imageAsset1));
        when(assetUrlBuilder.buildDownloadUrl("asset-1"))
                .thenReturn("http://localhost/cover1.jpg");

        // game-2: 500 views, 200 downloads -> score = 500*0.7 + 200*1.3 = 610
        when(cache.getViewCount("game-2")).thenReturn(500L);
        when(cache.getDownloadCount("game-2")).thenReturn(200L);
        when(devGameRepository.findById("game-2")).thenReturn(Optional.of(game2));
        when(devGameAssetRepository.findFirstByGameIdAndType("game-2", "image"))
                .thenReturn(Optional.of(imageAsset2));
        when(assetUrlBuilder.buildDownloadUrl("asset-2"))
                .thenReturn("http://localhost/cover2.jpg");

        // game-3: 2000 views, 50 downloads -> score = 2000*0.7 + 50*1.3 = 1465
        when(cache.getViewCount("game-3")).thenReturn(2000L);
        when(cache.getDownloadCount("game-3")).thenReturn(50L);
        when(devGameRepository.findById("game-3")).thenReturn(Optional.of(game3));
        when(devGameAssetRepository.findFirstByGameIdAndType("game-3", "image"))
                .thenReturn(Optional.empty());

        // When
        List<HotGameResponse> result = service.getHotGames(3);

        // Then
        assertNotNull(result);
        assertEquals(3, result.size());

        // 验证排序：game-3(1465) > game-1(830) > game-2(610)
        assertEquals("game-3", result.get(0).getId());
        assertEquals("Hot Game Three", result.get(0).getName());
        assertEquals(1465.0, result.get(0).getScore(), 0.01);
        assertNull(result.get(0).getCoverImageUrl()); // 无封面

        assertEquals("game-1", result.get(1).getId());
        assertEquals("Hot Game One", result.get(1).getName());
        assertEquals(830.0, result.get(1).getScore(), 0.01);
        assertEquals("http://localhost/cover1.jpg", result.get(1).getCoverImageUrl());

        assertEquals("game-2", result.get(2).getId());
        assertEquals(610.0, result.get(2).getScore(), 0.01);

        verify(cache, times(1)).getKeysByPrefix("devgame:view:");
        verify(cache, times(3)).getViewCount(anyString());
        verify(cache, times(3)).getDownloadCount(anyString());
        verify(devGameRepository, times(3)).findById(anyString());
    }

    @Test
    void testGetHotGames_LimitResults() {
        // Given - 有3个游戏，但只取前2个
        Set<String> viewKeys = new LinkedHashSet<>(Arrays.asList(
                "devgame:view:game-1",
                "devgame:view:game-2",
                "devgame:view:game-3"
        ));

        when(cache.getKeysByPrefix("devgame:view:")).thenReturn(viewKeys);

        when(cache.getViewCount("game-1")).thenReturn(1000L);
        when(cache.getDownloadCount("game-1")).thenReturn(100L);
        when(devGameRepository.findById("game-1")).thenReturn(Optional.of(game1));
        when(devGameAssetRepository.findFirstByGameIdAndType("game-1", "image"))
                .thenReturn(Optional.empty());

        when(cache.getViewCount("game-2")).thenReturn(500L);
        when(cache.getDownloadCount("game-2")).thenReturn(200L);
        when(devGameRepository.findById("game-2")).thenReturn(Optional.of(game2));
        when(devGameAssetRepository.findFirstByGameIdAndType("game-2", "image"))
                .thenReturn(Optional.empty());

        when(cache.getViewCount("game-3")).thenReturn(2000L);
        when(cache.getDownloadCount("game-3")).thenReturn(50L);
        when(devGameRepository.findById("game-3")).thenReturn(Optional.of(game3));
        when(devGameAssetRepository.findFirstByGameIdAndType("game-3", "image"))
                .thenReturn(Optional.empty());

        // When - 只取前2个
        List<HotGameResponse> result = service.getHotGames(2);

        // Then
        assertNotNull(result);
        assertEquals(2, result.size());

        // 应该返回分数最高的两个
        assertEquals("game-3", result.get(0).getId()); // 最高分
        assertEquals("game-1", result.get(1).getId()); // 第二高分
    }

    @Test
    void testGetHotGames_EmptyKeys_ReturnEmptyList() {
        // Given
        when(cache.getKeysByPrefix("devgame:view:")).thenReturn(Collections.emptySet());

        // When
        List<HotGameResponse> result = service.getHotGames(10);

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());

        verify(cache, times(1)).getKeysByPrefix("devgame:view:");
        verify(cache, never()).getViewCount(anyString());
        verify(devGameRepository, never()).findById(anyString());
    }

    @Test
    void testGetHotGames_NullKeys_ReturnEmptyList() {
        // Given
        when(cache.getKeysByPrefix("devgame:view:")).thenReturn(null);

        // When
        List<HotGameResponse> result = service.getHotGames(10);

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());

        verify(cache, times(1)).getKeysByPrefix("devgame:view:");
        verify(cache, never()).getViewCount(anyString());
    }

    @Test
    void testGetHotGames_GameNotFound_SkipThatGame() {
        // Given
        Set<String> viewKeys = new LinkedHashSet<>(Arrays.asList(
                "devgame:view:game-1",
                "devgame:view:game-999" // 不存在的游戏
        ));

        when(cache.getKeysByPrefix("devgame:view:")).thenReturn(viewKeys);

        when(cache.getViewCount("game-1")).thenReturn(1000L);
        when(cache.getDownloadCount("game-1")).thenReturn(100L);
        when(devGameRepository.findById("game-1")).thenReturn(Optional.of(game1));
        when(devGameAssetRepository.findFirstByGameIdAndType("game-1", "image"))
                .thenReturn(Optional.empty());

        when(cache.getViewCount("game-999")).thenReturn(500L);
        when(cache.getDownloadCount("game-999")).thenReturn(200L);
        when(devGameRepository.findById("game-999")).thenReturn(Optional.empty());

        // When
        List<HotGameResponse> result = service.getHotGames(10);

        // Then
        assertNotNull(result);
        assertEquals(1, result.size()); // 只返回找到的游戏
        assertEquals("game-1", result.get(0).getId());

        verify(devGameRepository, times(1)).findById("game-1");
        verify(devGameRepository, times(1)).findById("game-999");
    }

    @Test
    void testGetHotGames_ZeroViewsAndDownloads() {
        // Given
        Set<String> viewKeys = new LinkedHashSet<>(Collections.singletonList(
                "devgame:view:game-1"
        ));

        when(cache.getKeysByPrefix("devgame:view:")).thenReturn(viewKeys);
        when(cache.getViewCount("game-1")).thenReturn(0L);
        when(cache.getDownloadCount("game-1")).thenReturn(0L);
        when(devGameRepository.findById("game-1")).thenReturn(Optional.of(game1));
        when(devGameAssetRepository.findFirstByGameIdAndType("game-1", "image"))
                .thenReturn(Optional.empty());

        // When
        List<HotGameResponse> result = service.getHotGames(10);

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("game-1", result.get(0).getId());
        assertEquals(0.0, result.get(0).getScore()); // score = 0*0.7 + 0*1.3 = 0
    }

    @Test
    void testGetHotGames_SingleGame() {
        // Given
        Set<String> viewKeys = new LinkedHashSet<>(Collections.singletonList(
                "devgame:view:game-1"
        ));

        when(cache.getKeysByPrefix("devgame:view:")).thenReturn(viewKeys);
        when(cache.getViewCount("game-1")).thenReturn(100L);
        when(cache.getDownloadCount("game-1")).thenReturn(50L);
        when(devGameRepository.findById("game-1")).thenReturn(Optional.of(game1));
        when(devGameAssetRepository.findFirstByGameIdAndType("game-1", "image"))
                .thenReturn(Optional.of(imageAsset1));
        when(assetUrlBuilder.buildDownloadUrl("asset-1"))
                .thenReturn("http://localhost/cover1.jpg");

        // When
        List<HotGameResponse> result = service.getHotGames(10);

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("game-1", result.get(0).getId());
        assertEquals("Hot Game One", result.get(0).getName());
        assertEquals(135.0, result.get(0).getScore(), 0.01); // 100*0.7 + 50*1.3
        assertEquals("http://localhost/cover1.jpg", result.get(0).getCoverImageUrl());
    }

    @Test
    void testGetHotGames_LimitZero_ReturnEmpty() {
        // Given
        Set<String> viewKeys = new LinkedHashSet<>(Collections.singletonList(
                "devgame:view:game-1"
        ));

        when(cache.getKeysByPrefix("devgame:view:")).thenReturn(viewKeys);
        when(cache.getViewCount("game-1")).thenReturn(100L);
        when(cache.getDownloadCount("game-1")).thenReturn(50L);
        when(devGameRepository.findById("game-1")).thenReturn(Optional.of(game1));
        when(devGameAssetRepository.findFirstByGameIdAndType("game-1", "image"))
                .thenReturn(Optional.empty());

        // When
        List<HotGameResponse> result = service.getHotGames(0);

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testGetHotGames_HighDownloadLowView() {
        // Given - 测试算法权重：下载权重(1.3)高于浏览权重(0.7)
        Set<String> viewKeys = new LinkedHashSet<>(Arrays.asList(
                "devgame:view:game-1",
                "devgame:view:game-2"
        ));

        when(cache.getKeysByPrefix("devgame:view:")).thenReturn(viewKeys);

        // game-1: 高浏览低下载 -> 1000*0.7 + 10*1.3 = 713
        when(cache.getViewCount("game-1")).thenReturn(1000L);
        when(cache.getDownloadCount("game-1")).thenReturn(10L);
        when(devGameRepository.findById("game-1")).thenReturn(Optional.of(game1));
        when(devGameAssetRepository.findFirstByGameIdAndType("game-1", "image"))
                .thenReturn(Optional.empty());

        // game-2: 低浏览高下载 -> 100*0.7 + 600*1.3 = 850
        when(cache.getViewCount("game-2")).thenReturn(100L);
        when(cache.getDownloadCount("game-2")).thenReturn(600L);
        when(devGameRepository.findById("game-2")).thenReturn(Optional.of(game2));
        when(devGameAssetRepository.findFirstByGameIdAndType("game-2", "image"))
                .thenReturn(Optional.empty());

        // When
        List<HotGameResponse> result = service.getHotGames(10);

        // Then
        assertEquals(2, result.size());
        // game-2 应该排在前面，因为下载权重更高
        assertEquals("game-2", result.get(0).getId());
        assertEquals(850.0, result.get(0).getScore(), 0.01);
        assertEquals("game-1", result.get(1).getId());
        assertEquals(713.0, result.get(1).getScore(), 0.01);
    }

    @Test
    void testGetHotGames_SameScore_MaintainOrder() {
        // Given - 测试相同分数的情况
        Set<String> viewKeys = new LinkedHashSet<>(Arrays.asList(
                "devgame:view:game-1",
                "devgame:view:game-2"
        ));

        when(cache.getKeysByPrefix("devgame:view:")).thenReturn(viewKeys);

        // 两个游戏相同的分数
        when(cache.getViewCount("game-1")).thenReturn(100L);
        when(cache.getDownloadCount("game-1")).thenReturn(100L);
        when(devGameRepository.findById("game-1")).thenReturn(Optional.of(game1));
        when(devGameAssetRepository.findFirstByGameIdAndType("game-1", "image"))
                .thenReturn(Optional.empty());

        when(cache.getViewCount("game-2")).thenReturn(100L);
        when(cache.getDownloadCount("game-2")).thenReturn(100L);
        when(devGameRepository.findById("game-2")).thenReturn(Optional.of(game2));
        when(devGameAssetRepository.findFirstByGameIdAndType("game-2", "image"))
                .thenReturn(Optional.empty());

        // When
        List<HotGameResponse> result = service.getHotGames(10);

        // Then
        assertEquals(2, result.size());
        // 分数相同: 100*0.7 + 100*1.3 = 200
        assertEquals(200.0, result.get(0).getScore(), 0.01);
        assertEquals(200.0, result.get(1).getScore(), 0.01);
    }

    @Test
    void testGetHotGames_AllGamesNoCover() {
        // Given
        Set<String> viewKeys = new LinkedHashSet<>(Arrays.asList(
                "devgame:view:game-1",
                "devgame:view:game-2"
        ));

        when(cache.getKeysByPrefix("devgame:view:")).thenReturn(viewKeys);

        when(cache.getViewCount("game-1")).thenReturn(100L);
        when(cache.getDownloadCount("game-1")).thenReturn(50L);
        when(devGameRepository.findById("game-1")).thenReturn(Optional.of(game1));
        when(devGameAssetRepository.findFirstByGameIdAndType("game-1", "image"))
                .thenReturn(Optional.empty());

        when(cache.getViewCount("game-2")).thenReturn(200L);
        when(cache.getDownloadCount("game-2")).thenReturn(100L);
        when(devGameRepository.findById("game-2")).thenReturn(Optional.of(game2));
        when(devGameAssetRepository.findFirstByGameIdAndType("game-2", "image"))
                .thenReturn(Optional.empty());

        // When
        List<HotGameResponse> result = service.getHotGames(10);

        // Then
        assertEquals(2, result.size());
        assertNull(result.get(0).getCoverImageUrl());
        assertNull(result.get(1).getCoverImageUrl());

        verify(assetUrlBuilder, never()).buildDownloadUrl(anyString());
    }
}