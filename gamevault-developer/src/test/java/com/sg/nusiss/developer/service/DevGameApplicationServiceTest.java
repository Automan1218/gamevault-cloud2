package com.sg.nusiss.developer.service;

import com.sg.nusiss.developer.dto.DevGameResponse;
import com.sg.nusiss.developer.dto.DevGameUploadRequest;
import com.sg.nusiss.developer.dto.OperationResult;
import com.sg.nusiss.developer.entity.DevGame;
import com.sg.nusiss.developer.entity.DevGameAsset;
import com.sg.nusiss.developer.entity.DeveloperProfile;
import com.sg.nusiss.developer.repository.DevGameAssetRepository;
import com.sg.nusiss.developer.repository.DevGameRepository;
import com.sg.nusiss.developer.repository.DevGameStatisticsRepository;
import com.sg.nusiss.developer.repository.DeveloperProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * @ClassName DevGameApplicationServiceTest
 * @Author HUANG ZHENJIA
 * @Date 2025/11/14
 * @Description DevGameApplicationService 单元测试
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DevGameApplicationServiceTest {

    @Mock
    private DevGameRepository devGameRepository;

    @Mock
    private DevGameAssetRepository devGameAssetRepository;

    @Mock
    private DeveloperProfileRepository developerProfileRepository;

    @Mock
    private DevGameStatisticsRepository devGameStatisticsRepository;

    @Mock
    private MultipartFile imageFile;

    @Mock
    private MultipartFile videoFile;

    @Mock
    private MultipartFile zipFile;

    @InjectMocks
    private DevGameApplicationService service;

    @TempDir
    Path tempDir;

    private DeveloperProfile testProfile;
    private DevGame testGame;
    private DevGameUploadRequest uploadRequest;

    @BeforeEach
    void setUp() {
        // 设置临时存储路径
        ReflectionTestUtils.setField(service, "assetStoragePath", tempDir.toString());

        // 初始化测试数据
        testProfile = new DeveloperProfile();
        testProfile.setId("profile-123");
        testProfile.setUserId("user-123");
        testProfile.setProjectCount(5);

        testGame = new DevGame(
                "game-123",
                "profile-123",
                "Test Game",
                "Test Description",
                LocalDateTime.now(),
                LocalDateTime.now(),
                LocalDateTime.now()
        );

        uploadRequest = new DevGameUploadRequest();
        uploadRequest.setDeveloperId("profile-123");
        uploadRequest.setName("New Game");
        uploadRequest.setDescription("New Game Description");
        uploadRequest.setReleaseDate(LocalDateTime.now());
    }

    // ==================== uploadGame 方法测试 ====================

    @Test
    void testUploadGame_Success() throws IOException {
        // Given
        when(developerProfileRepository.findById("profile-123")).thenReturn(Optional.of(testProfile));
        doNothing().when(developerProfileRepository).syncProjectCount(anyString());

        // Mock file behaviors
        when(imageFile.getSize()).thenReturn(1024L);
        when(imageFile.getContentType()).thenReturn("image/jpeg");
        when(imageFile.getOriginalFilename()).thenReturn("test.jpg");
        doNothing().when(imageFile).transferTo(any(File.class));

        when(videoFile.getSize()).thenReturn(2048L);
        when(videoFile.getContentType()).thenReturn("video/mp4");
        when(videoFile.getOriginalFilename()).thenReturn("test.mp4");
        doNothing().when(videoFile).transferTo(any(File.class));

        when(zipFile.getSize()).thenReturn(3072L);
        when(zipFile.getContentType()).thenReturn("application/zip");
        when(zipFile.getOriginalFilename()).thenReturn("test.zip");
        doNothing().when(zipFile).transferTo(any(File.class));

        uploadRequest.setImage(imageFile);
        uploadRequest.setVideo(videoFile);
        uploadRequest.setZip(zipFile);

        // When
        DevGameResponse response = service.uploadGame(uploadRequest);

        // Then
        assertNotNull(response);
        assertEquals("New Game", response.getName());
        assertEquals("New Game Description", response.getDescription());
        assertNotNull(response.getVideoUrl());
        assertNotNull(response.getZipUrl());

        verify(devGameRepository, times(1)).insert(any(DevGame.class));
        verify(devGameAssetRepository, times(3)).insert(any(DevGameAsset.class));
        verify(developerProfileRepository, times(1)).syncProjectCount("profile-123");
    }

    @Test
    void testUploadGame_DeveloperNotFound_ThrowException() {
        // Given
        when(developerProfileRepository.findById("profile-123")).thenReturn(Optional.empty());

        // When & Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            service.uploadGame(uploadRequest);
        });

        assertEquals("Developer profile not found", exception.getMessage());
        verify(devGameRepository, never()).insert(any(DevGame.class));
    }

    @Test
    void testUploadGame_FileSizeExceeded_ThrowException() throws IOException {
        // Given
        when(developerProfileRepository.findById("profile-123")).thenReturn(Optional.of(testProfile));
        when(imageFile.getSize()).thenReturn(300L * 1024 * 1024); // 300 MB
        uploadRequest.setImage(imageFile);
        uploadRequest.setVideo(videoFile);
        uploadRequest.setZip(zipFile);

        // When & Then
        assertThrows(RuntimeException.class, () -> {
            service.uploadGame(uploadRequest);
        });
    }

    @Test
    void testUploadGame_InvalidImageType_ThrowException() throws IOException {
        // Given
        when(developerProfileRepository.findById("profile-123")).thenReturn(Optional.of(testProfile));
        when(imageFile.getSize()).thenReturn(1024L);
        when(imageFile.getContentType()).thenReturn("application/pdf");
        uploadRequest.setImage(imageFile);
        uploadRequest.setVideo(videoFile);
        uploadRequest.setZip(zipFile);

        // When & Then
        assertThrows(RuntimeException.class, () -> {
            service.uploadGame(uploadRequest);
        });
    }

    @Test
    void testUploadGame_InvalidVideoType_ThrowException() throws IOException {
        // Given
        when(developerProfileRepository.findById("profile-123")).thenReturn(Optional.of(testProfile));
        when(imageFile.getSize()).thenReturn(1024L);
        when(imageFile.getContentType()).thenReturn("image/jpeg");
        when(imageFile.getOriginalFilename()).thenReturn("test.jpg");
        doNothing().when(imageFile).transferTo(any(File.class));

        when(videoFile.getSize()).thenReturn(2048L);
        when(videoFile.getContentType()).thenReturn("application/pdf");

        uploadRequest.setImage(imageFile);
        uploadRequest.setVideo(videoFile);
        uploadRequest.setZip(zipFile);

        // When & Then
        assertThrows(RuntimeException.class, () -> {
            service.uploadGame(uploadRequest);
        });
    }

    @Test
    void testUploadGame_InvalidZipType_ThrowException() throws IOException {
        // Given
        when(developerProfileRepository.findById("profile-123")).thenReturn(Optional.of(testProfile));
        when(imageFile.getSize()).thenReturn(1024L);
        when(imageFile.getContentType()).thenReturn("image/jpeg");
        when(imageFile.getOriginalFilename()).thenReturn("test.jpg");
        doNothing().when(imageFile).transferTo(any(File.class));

        when(videoFile.getSize()).thenReturn(2048L);
        when(videoFile.getContentType()).thenReturn("video/mp4");
        when(videoFile.getOriginalFilename()).thenReturn("test.mp4");
        doNothing().when(videoFile).transferTo(any(File.class));

        when(zipFile.getSize()).thenReturn(3072L);
        when(zipFile.getContentType()).thenReturn("application/pdf");

        uploadRequest.setImage(imageFile);
        uploadRequest.setVideo(videoFile);
        uploadRequest.setZip(zipFile);

        // When & Then
        assertThrows(RuntimeException.class, () -> {
            service.uploadGame(uploadRequest);
        });
    }

    // ==================== deleteGame 方法测试 ====================

    @Test
    void testDeleteGame_Success() {
        // Given
        when(developerProfileRepository.findByUserId("user-123")).thenReturn(Optional.of(testProfile));
        when(devGameRepository.findById("game-123")).thenReturn(Optional.of(testGame));
        doNothing().when(developerProfileRepository).syncProjectCount("profile-123");

        // When
        OperationResult result = service.deleteGame("user-123", "game-123");

        // Then
        assertTrue(result.isSuccess());
        assertEquals("Game deleted successfully: game-123", result.getMessage());

        verify(devGameAssetRepository, times(1)).deleteByGameId("game-123");
        verify(devGameStatisticsRepository, times(1)).deleteByGameId("game-123");
        verify(devGameRepository, times(1)).deleteById("game-123");
        verify(developerProfileRepository, times(1)).syncProjectCount("profile-123");
    }

    @Test
    void testDeleteGame_DeveloperNotFound_ReturnFailure() {
        // Given
        when(developerProfileRepository.findByUserId("user-123")).thenReturn(Optional.empty());

        // When
        OperationResult result = service.deleteGame("user-123", "game-123");

        // Then
        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Developer profile not found"));

        verify(devGameRepository, never()).deleteById(anyString());
    }

    @Test
    void testDeleteGame_GameNotFound_ReturnFailure() {
        // Given
        when(developerProfileRepository.findByUserId("user-123")).thenReturn(Optional.of(testProfile));
        when(devGameRepository.findById("game-123")).thenReturn(Optional.empty());

        // When
        OperationResult result = service.deleteGame("user-123", "game-123");

        // Then
        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Game not found"));

        verify(devGameRepository, never()).deleteById(anyString());
    }

    @Test
    void testDeleteGame_Unauthorized_ReturnFailure() {
        // Given
        testGame.setDeveloperProfileId("different-profile-id");
        when(developerProfileRepository.findByUserId("user-123")).thenReturn(Optional.of(testProfile));
        when(devGameRepository.findById("game-123")).thenReturn(Optional.of(testGame));

        // When
        OperationResult result = service.deleteGame("user-123", "game-123");

        // Then
        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Unauthorized"));

        verify(devGameRepository, never()).deleteById(anyString());
    }

    @Test
    void testDeleteGame_Exception_ReturnFailure() {
        // Given
        when(developerProfileRepository.findByUserId("user-123")).thenReturn(Optional.of(testProfile));
        when(devGameRepository.findById("game-123")).thenReturn(Optional.of(testGame));

        // 添加异常 mock - 这是关键！
        doThrow(new RuntimeException("Database error"))
                .when(devGameAssetRepository).deleteByGameId("game-123");

        // When
        OperationResult result = service.deleteGame("user-123", "game-123");

        // Then
        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertNotNull(result.getMessage());
        assertTrue(result.getMessage().contains("Failed to delete game"));
    }

    // ==================== updateGame 方法测试 ====================

    @Test
    void testUpdateGame_Success_WithAllFiles() throws IOException {
        // Given
        when(devGameRepository.findById("game-123")).thenReturn(Optional.of(testGame));
        when(developerProfileRepository.findById("profile-123")).thenReturn(Optional.of(testProfile));

        // Mock file behaviors
        when(imageFile.isEmpty()).thenReturn(false);
        when(imageFile.getSize()).thenReturn(1024L);
        when(imageFile.getContentType()).thenReturn("image/jpeg");
        when(imageFile.getOriginalFilename()).thenReturn("updated.jpg");
        doNothing().when(imageFile).transferTo(any(File.class));

        when(videoFile.isEmpty()).thenReturn(false);
        when(videoFile.getSize()).thenReturn(2048L);
        when(videoFile.getContentType()).thenReturn("video/mp4");
        when(videoFile.getOriginalFilename()).thenReturn("updated.mp4");
        doNothing().when(videoFile).transferTo(any(File.class));

        when(zipFile.isEmpty()).thenReturn(false);
        when(zipFile.getSize()).thenReturn(3072L);
        when(zipFile.getContentType()).thenReturn("application/zip");
        when(zipFile.getOriginalFilename()).thenReturn("updated.zip");
        doNothing().when(zipFile).transferTo(any(File.class));

        // When
        DevGameResponse response = service.updateGame(
                "game-123",
                "Updated Game",
                "Updated Description",
                "2025-12-31T23:59:59Z",
                null,
                imageFile,
                videoFile,
                zipFile
        );

        // Then
        assertNotNull(response);
        assertEquals("game-123", response.getId());
        assertEquals("Updated Game", response.getName());
        assertEquals("Updated Description", response.getDescription());
        assertNotNull(response.getVideoUrl());
        assertNotNull(response.getZipUrl());

        verify(devGameRepository, times(1)).update(any(DevGame.class));
        verify(devGameAssetRepository, times(3)).insert(any(DevGameAsset.class));
    }

    @Test
    void testUpdateGame_Success_WithoutFiles() {
        // Given
        when(devGameRepository.findById("game-123")).thenReturn(Optional.of(testGame));
        when(developerProfileRepository.findById("profile-123")).thenReturn(Optional.of(testProfile));

        // When
        DevGameResponse response = service.updateGame(
                "game-123",
                "Updated Game",
                "Updated Description",
                null,
                null,
                null,
                null,
                null
        );

        // Then
        assertNotNull(response);
        assertEquals("game-123", response.getId());
        assertEquals("Updated Game", response.getName());
        assertNull(response.getVideoUrl());
        assertNull(response.getZipUrl());

        verify(devGameRepository, times(1)).update(any(DevGame.class));
        verify(devGameAssetRepository, never()).insert(any(DevGameAsset.class));
    }

    @Test
    void testUpdateGame_GameNotFound_ThrowException() {
        // Given
        when(devGameRepository.findById("game-123")).thenReturn(Optional.empty());

        // When & Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            service.updateGame("game-123", "Updated", "Description", null, null, null, null, null);
        });

        assertTrue(exception.getMessage().contains("Game not found"));
        verify(devGameRepository, never()).update(any(DevGame.class));
    }

    @Test
    void testUpdateGame_DeveloperNotFound_ThrowException() {
        // Given
        when(devGameRepository.findById("game-123")).thenReturn(Optional.of(testGame));
        when(developerProfileRepository.findById("profile-123")).thenReturn(Optional.empty());

        // When & Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            service.updateGame("game-123", "Updated", "Description", null, null, null, null, null);
        });

        assertTrue(exception.getMessage().contains("Developer profile not found"));
        verify(devGameRepository, never()).update(any(DevGame.class));
    }

    @Test
    void testUpdateGame_InvalidReleaseDateFormat_ThrowException() {
        // Given
        when(devGameRepository.findById("game-123")).thenReturn(Optional.of(testGame));
        when(developerProfileRepository.findById("profile-123")).thenReturn(Optional.of(testProfile));

        // When & Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            service.updateGame(
                    "game-123",
                    "Updated",
                    "Description",
                    "invalid-date",
                    null,
                    null,
                    null,
                    null
            );
        });

        assertTrue(exception.getMessage().contains("Invalid releaseDate format"));
    }
}