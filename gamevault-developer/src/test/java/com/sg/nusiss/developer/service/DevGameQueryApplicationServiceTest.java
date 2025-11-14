package com.sg.nusiss.developer.service;

import com.sg.nusiss.developer.dto.DevGameListResponse;
import com.sg.nusiss.developer.dto.DevGameResponse;
import com.sg.nusiss.developer.dto.DevGameSummaryResponse;
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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * @ClassName DevGameQueryApplicationServiceTest
 * @Author HUANG ZHENJIA
 * @Date 2025/11/14
 * @Description DevGameQueryApplicationService 单元测试
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DevGameQueryApplicationServiceTest {

    @Mock
    private DevGameRepository devGameRepository;

    @Mock
    private DevGameAssetRepository devGameAssetRepository;

    @Mock
    private AssetUrlBuilder assetUrlBuilder;

    @InjectMocks
    private DevGameQueryApplicationService service;

    private DevGame testGame1;
    private DevGame testGame2;
    private DevGameAsset imageAsset;
    private DevGameAsset videoAsset;
    private DevGameAsset zipAsset;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "assetBaseUrl", "http://localhost:8080/assets");

        LocalDateTime now = LocalDateTime.now();

        testGame1 = new DevGame(
                "game-1",
                "developer-1",
                "Game One",
                "Description One",
                now.minusDays(10),
                now.minusDays(5),
                now
        );

        testGame2 = new DevGame(
                "game-2",
                "developer-1",
                "Game Two",
                "Description Two",
                now.minusDays(5),
                now.minusDays(3),
                now
        );

        imageAsset = new DevGameAsset(
                "asset-image-1",
                "game-1",
                "image",
                "cover.jpg",
                "/path/to/cover.jpg",
                1024L,
                "image/jpeg",
                now
        );

        videoAsset = new DevGameAsset(
                "asset-video-1",
                "game-1",
                "video",
                "trailer.mp4",
                "/path/to/trailer.mp4",
                2048L,
                "video/mp4",
                now
        );

        zipAsset = new DevGameAsset(
                "asset-zip-1",
                "game-1",
                "zip",
                "game.zip",
                "/path/to/game.zip",
                3072L,
                "application/zip",
                now
        );
    }

    // ==================== listDevGamesWithCover 方法测试 ====================

    @Test
    void testListDevGamesWithCover_Success_WithImages() {
        // Given
        when(devGameRepository.findByDeveloperProfileId("developer-1"))
                .thenReturn(Arrays.asList(testGame1, testGame2));

        when(devGameAssetRepository.findFirstByGameIdAndType("game-1", "image"))
                .thenReturn(Optional.of(imageAsset));
        when(devGameAssetRepository.findFirstByGameIdAndType("game-2", "image"))
                .thenReturn(Optional.empty());

        when(assetUrlBuilder.buildDownloadUrl("asset-image-1"))
                .thenReturn("http://localhost:8080/assets/download/asset-image-1");

        // When
        List<DevGameSummaryResponse> result = service.listDevGamesWithCover("developer-1");

        // Then
        assertNotNull(result);
        assertEquals(2, result.size());

        DevGameSummaryResponse game1Response = result.get(0);
        assertEquals("game-1", game1Response.getId());
        assertEquals("Game One", game1Response.getName());
        assertEquals("Description One", game1Response.getDescription());
        assertEquals("http://localhost:8080/assets/download/asset-image-1", game1Response.getImageUrl());

        DevGameSummaryResponse game2Response = result.get(1);
        assertEquals("game-2", game2Response.getId());
        assertEquals("Game Two", game2Response.getName());
        assertNull(game2Response.getImageUrl());

        verify(devGameRepository, times(1)).findByDeveloperProfileId("developer-1");
        verify(devGameAssetRepository, times(2)).findFirstByGameIdAndType(anyString(), eq("image"));
        verify(assetUrlBuilder, times(1)).buildDownloadUrl("asset-image-1");
    }

    @Test
    void testListDevGamesWithCover_EmptyList() {
        // Given
        when(devGameRepository.findByDeveloperProfileId("developer-1"))
                .thenReturn(Collections.emptyList());

        // When
        List<DevGameSummaryResponse> result = service.listDevGamesWithCover("developer-1");

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());

        verify(devGameRepository, times(1)).findByDeveloperProfileId("developer-1");
        verify(devGameAssetRepository, never()).findFirstByGameIdAndType(anyString(), anyString());
    }

    @Test
    void testListDevGamesWithCover_NoImages() {
        // Given
        when(devGameRepository.findByDeveloperProfileId("developer-1"))
                .thenReturn(Arrays.asList(testGame1, testGame2));

        when(devGameAssetRepository.findFirstByGameIdAndType(anyString(), eq("image")))
                .thenReturn(Optional.empty());

        // When
        List<DevGameSummaryResponse> result = service.listDevGamesWithCover("developer-1");

        // Then
        assertNotNull(result);
        assertEquals(2, result.size());
        assertNull(result.get(0).getImageUrl());
        assertNull(result.get(1).getImageUrl());

        verify(assetUrlBuilder, never()).buildDownloadUrl(anyString());
    }

    // ==================== queryDevGameDetails 方法测试 ====================

    @Test
    void testQueryDevGameDetails_Success_WithAllAssets() {
        // Given
        when(devGameRepository.findById("game-1")).thenReturn(Optional.of(testGame1));

        when(devGameAssetRepository.findFirstByGameIdAndType("game-1", "image"))
                .thenReturn(Optional.of(imageAsset));
        when(devGameAssetRepository.findFirstByGameIdAndType("game-1", "video"))
                .thenReturn(Optional.of(videoAsset));
        when(devGameAssetRepository.findFirstByGameIdAndType("game-1", "zip"))
                .thenReturn(Optional.of(zipAsset));

        when(assetUrlBuilder.buildDownloadUrl("asset-image-1"))
                .thenReturn("http://localhost:8080/assets/download/asset-image-1");
        when(assetUrlBuilder.buildDownloadUrl("asset-video-1"))
                .thenReturn("http://localhost:8080/assets/download/asset-video-1");
        when(assetUrlBuilder.buildDownloadUrl("asset-zip-1"))
                .thenReturn("http://localhost:8080/assets/download/asset-zip-1");

        // When
        DevGameResponse result = service.queryDevGameDetails("game-1");

        // Then
        assertNotNull(result);
        assertEquals("game-1", result.getId());
        assertEquals("Game One", result.getName());
        assertEquals("Description One", result.getDescription());
        assertEquals("http://localhost:8080/assets/download/asset-video-1", result.getVideoUrl());
        assertEquals("http://localhost:8080/assets/download/asset-zip-1", result.getZipUrl());

        verify(devGameRepository, times(1)).findById("game-1");
        verify(devGameAssetRepository, times(1)).findFirstByGameIdAndType("game-1", "image");
        verify(devGameAssetRepository, times(1)).findFirstByGameIdAndType("game-1", "video");
        verify(devGameAssetRepository, times(1)).findFirstByGameIdAndType("game-1", "zip");
        verify(assetUrlBuilder, times(3)).buildDownloadUrl(anyString());
    }

    @Test
    void testQueryDevGameDetails_Success_WithoutAssets() {
        // Given
        when(devGameRepository.findById("game-1")).thenReturn(Optional.of(testGame1));

        when(devGameAssetRepository.findFirstByGameIdAndType(anyString(), anyString()))
                .thenReturn(Optional.empty());

        // When
        DevGameResponse result = service.queryDevGameDetails("game-1");

        // Then
        assertNotNull(result);
        assertEquals("game-1", result.getId());
        assertEquals("Game One", result.getName());
        assertNull(result.getVideoUrl());
        assertNull(result.getZipUrl());

        verify(assetUrlBuilder, never()).buildDownloadUrl(anyString());
    }

    @Test
    void testQueryDevGameDetails_GameNotFound_ThrowException() {
        // Given
        when(devGameRepository.findById("game-999")).thenReturn(Optional.empty());

        // When & Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            service.queryDevGameDetails("game-999");
        });

        assertTrue(exception.getMessage().contains("Game not found"));
        verify(devGameAssetRepository, never()).findFirstByGameIdAndType(anyString(), anyString());
    }

    @Test
    void testQueryDevGameDetails_Success_PartialAssets() {
        // Given
        when(devGameRepository.findById("game-1")).thenReturn(Optional.of(testGame1));

        when(devGameAssetRepository.findFirstByGameIdAndType("game-1", "image"))
                .thenReturn(Optional.of(imageAsset));
        when(devGameAssetRepository.findFirstByGameIdAndType("game-1", "video"))
                .thenReturn(Optional.empty());
        when(devGameAssetRepository.findFirstByGameIdAndType("game-1", "zip"))
                .thenReturn(Optional.of(zipAsset));

        when(assetUrlBuilder.buildDownloadUrl("asset-image-1"))
                .thenReturn("http://localhost:8080/assets/download/asset-image-1");
        when(assetUrlBuilder.buildDownloadUrl("asset-zip-1"))
                .thenReturn("http://localhost:8080/assets/download/asset-zip-1");

        // When
        DevGameResponse result = service.queryDevGameDetails("game-1");

        // Then
        assertNotNull(result);
        assertNull(result.getVideoUrl());
        assertEquals("http://localhost:8080/assets/download/asset-zip-1", result.getZipUrl());
    }

    // ==================== listAllGames 方法测试 ====================

    @Test
    void testListAllGames_Success_FirstPage() {
        // Given
        when(devGameRepository.findAllPaged(0, 10))
                .thenReturn(Arrays.asList(testGame1, testGame2));
        when(devGameRepository.countAll()).thenReturn(25L);

        when(devGameAssetRepository.findFirstByGameIdAndType("game-1", "image"))
                .thenReturn(Optional.of(imageAsset));
        when(devGameAssetRepository.findFirstByGameIdAndType("game-2", "image"))
                .thenReturn(Optional.empty());

        when(assetUrlBuilder.buildDownloadUrl("asset-image-1"))
                .thenReturn("http://localhost:8080/assets/download/asset-image-1");

        // When
        DevGameListResponse result = service.listAllGames(1, 10);

        // Then
        assertNotNull(result);
        assertEquals(2, result.getGames().size());
        assertEquals(1, result.getCurrentPage());
        assertEquals(10, result.getPageSize());
        assertEquals(25L, result.getTotalCount());
        assertEquals(3, result.getTotalPages());

        verify(devGameRepository, times(1)).findAllPaged(0, 10);
        verify(devGameRepository, times(1)).countAll();
    }

    @Test
    void testListAllGames_Success_SecondPage() {
        // Given
        when(devGameRepository.findAllPaged(10, 10))
                .thenReturn(Arrays.asList(testGame1));
        when(devGameRepository.countAll()).thenReturn(25L);

        when(devGameAssetRepository.findFirstByGameIdAndType("game-1", "image"))
                .thenReturn(Optional.of(imageAsset));

        when(assetUrlBuilder.buildDownloadUrl("asset-image-1"))
                .thenReturn("http://localhost:8080/assets/download/asset-image-1");

        // When
        DevGameListResponse result = service.listAllGames(2, 10);

        // Then
        assertNotNull(result);
        assertEquals(1, result.getGames().size());
        assertEquals(2, result.getCurrentPage());
        assertEquals(10, result.getPageSize());
        assertEquals(25L, result.getTotalCount());
        assertEquals(3, result.getTotalPages());

        verify(devGameRepository, times(1)).findAllPaged(10, 10);
    }

    @Test
    void testListAllGames_EmptyResult() {
        // Given
        when(devGameRepository.findAllPaged(0, 10))
                .thenReturn(Collections.emptyList());
        when(devGameRepository.countAll()).thenReturn(0L);

        // When
        DevGameListResponse result = service.listAllGames(1, 10);

        // Then
        assertNotNull(result);
        assertTrue(result.getGames().isEmpty());
        assertEquals(1, result.getCurrentPage());
        assertEquals(10, result.getPageSize());
        assertEquals(0L, result.getTotalCount());
        assertEquals(0, result.getTotalPages());
    }

    @Test
    void testListAllGames_NegativePageNumber_TreatedAsZero() {
        // Given
        when(devGameRepository.findAllPaged(0, 10))
                .thenReturn(Arrays.asList(testGame1));
        when(devGameRepository.countAll()).thenReturn(15L);

        when(devGameAssetRepository.findFirstByGameIdAndType("game-1", "image"))
                .thenReturn(Optional.of(imageAsset));

        when(assetUrlBuilder.buildDownloadUrl("asset-image-1"))
                .thenReturn("http://localhost:8080/assets/download/asset-image-1");

        // When
        DevGameListResponse result = service.listAllGames(-1, 10);

        // Then
        assertNotNull(result);
        assertEquals(1, result.getGames().size());
        assertEquals(-1, result.getCurrentPage());

        // Verify offset is 0 (negative page treated as 0)
        verify(devGameRepository, times(1)).findAllPaged(0, 10);
    }

    @Test
    void testListAllGames_CustomPageSize() {
        // Given
        when(devGameRepository.findAllPaged(0, 5))
                .thenReturn(Arrays.asList(testGame1, testGame2));
        when(devGameRepository.countAll()).thenReturn(25L);

        when(devGameAssetRepository.findFirstByGameIdAndType(anyString(), eq("image")))
                .thenReturn(Optional.empty());

        // When
        DevGameListResponse result = service.listAllGames(1, 5);

        // Then
        assertNotNull(result);
        assertEquals(2, result.getGames().size());
        assertEquals(5, result.getPageSize());
        assertEquals(5, result.getTotalPages()); // 25 / 5 = 5 pages

        verify(devGameRepository, times(1)).findAllPaged(0, 5);
    }

    @Test
    void testListAllGames_LastPagePartial() {
        // Given
        when(devGameRepository.findAllPaged(20, 10))
                .thenReturn(Arrays.asList(testGame1)); // Only 1 game on last page
        when(devGameRepository.countAll()).thenReturn(21L);

        when(devGameAssetRepository.findFirstByGameIdAndType("game-1", "image"))
                .thenReturn(Optional.empty());

        // When
        DevGameListResponse result = service.listAllGames(3, 10);

        // Then
        assertNotNull(result);
        assertEquals(1, result.getGames().size());
        assertEquals(3, result.getCurrentPage());
        assertEquals(3, result.getTotalPages()); // ceil(21 / 10) = 3 pages

        verify(devGameRepository, times(1)).findAllPaged(20, 10);
    }
}