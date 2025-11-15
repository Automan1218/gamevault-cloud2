package com.sg.nusiss.shopping.service.shopping;

import com.sg.nusiss.shopping.dto.shopping.GameDTO;
import com.sg.nusiss.shopping.entity.shopping.Game;
import com.sg.nusiss.shopping.repository.library.UnusedGameActivationCodeRepository;
import com.sg.nusiss.shopping.repository.shopping.GameRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * GameService 单元测试
 *
 * 测试要点:
 * 1. 游戏CRUD操作
 * 2. 查询功能（按标题、类型、平台）
 * 3. 热门折扣游戏
 * 4. 激活码库存管理
 * 5. 参数验证
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GameService 单元测试")
class GameServiceTest {

    @Mock
    private GameRepository gameRepository;

    @Mock
    private GameActivationCodeService activationCodeService;

    @Mock
    private UnusedGameActivationCodeRepository unusedRepo;

    @InjectMocks
    private GameService gameService;

    private Long testGameId;
    private Game testGame;
    private GameDTO testGameDTO;

    @BeforeEach
    void setUp() {
        testGameId = 1L;
        testGame = createTestGame(testGameId, "Test Game", "Developer", new BigDecimal("59.99"));
        testGameDTO = createTestGameDTO(testGameId, "Test Game", "Developer", new BigDecimal("59.99"));
        
        // 设置 TARGET_STOCK 字段的值（因为 @Value 在单元测试中不会被注入）
        ReflectionTestUtils.setField(gameService, "TARGET_STOCK", 30);
    }

    // ========================================
    // findAll 测试
    // ========================================

    @Test
    @DisplayName("findAll - 成功获取所有游戏")
    void testFindAll_Success() {
        // Given
        List<Game> games = Arrays.asList(
                createTestGame(1L, "Game 1", "Dev 1", new BigDecimal("49.99")),
                createTestGame(2L, "Game 2", "Dev 2", new BigDecimal("59.99"))
        );
        when(gameRepository.findAll()).thenReturn(games);

        // When
        List<GameDTO> result = gameService.findAll();

        // Then
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("Game 1", result.get(0).getTitle());
        assertEquals("Game 2", result.get(1).getTitle());
        verify(gameRepository, times(1)).findAll();
    }

    @Test
    @DisplayName("findAll - 空列表")
    void testFindAll_EmptyList() {
        // Given
        when(gameRepository.findAll()).thenReturn(Arrays.asList());

        // When
        List<GameDTO> result = gameService.findAll();

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // ========================================
    // findById 测试
    // ========================================

    @Test
    @DisplayName("findById - 成功获取游戏")
    void testFindById_Success() {
        // Given
        when(gameRepository.findById(testGameId)).thenReturn(Optional.of(testGame));

        // When
        Optional<GameDTO> result = gameService.findById(testGameId);

        // Then
        assertTrue(result.isPresent());
        assertEquals(testGameId, result.get().getGameId());
        assertEquals("Test Game", result.get().getTitle());
        verify(gameRepository, times(1)).findById(testGameId);
    }

    @Test
    @DisplayName("findById - 游戏不存在")
    void testFindById_NotFound() {
        // Given
        when(gameRepository.findById(testGameId)).thenReturn(Optional.empty());

        // When
        Optional<GameDTO> result = gameService.findById(testGameId);

        // Then
        assertFalse(result.isPresent());
    }

    // ========================================
    // searchByTitle 测试
    // ========================================

    @Test
    @DisplayName("searchByTitle - 成功搜索游戏")
    void testSearchByTitle_Success() {
        // Given
        String keyword = "Test";
        List<Game> games = Arrays.asList(testGame);
        when(gameRepository.findByTitleContainingIgnoreCase(keyword)).thenReturn(games);

        // When
        List<GameDTO> result = gameService.searchByTitle(keyword);

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("Test Game", result.get(0).getTitle());
        verify(gameRepository, times(1)).findByTitleContainingIgnoreCase(keyword);
    }

    @Test
    @DisplayName("searchByTitle - 无匹配结果")
    void testSearchByTitle_NoMatch() {
        // Given
        String keyword = "NonExistent";
        when(gameRepository.findByTitleContainingIgnoreCase(keyword)).thenReturn(Arrays.asList());

        // When
        List<GameDTO> result = gameService.searchByTitle(keyword);

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // ========================================
    // findByGenre 测试
    // ========================================

    @Test
    @DisplayName("findByGenre - 成功按类型查找")
    void testFindByGenre_Success() {
        // Given
        String genre = "RPG";
        testGame.setGenre(genre);
        List<Game> games = Arrays.asList(testGame);
        when(gameRepository.findByGenre(genre)).thenReturn(games);

        // When
        List<GameDTO> result = gameService.findByGenre(genre);

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(genre, result.get(0).getGenre());
        verify(gameRepository, times(1)).findByGenre(genre);
    }

    // ========================================
    // findByPlatform 测试
    // ========================================

    @Test
    @DisplayName("findByPlatform - 成功按平台查找")
    void testFindByPlatform_Success() {
        // Given
        String platform = "PC";
        testGame.setPlatform(platform);
        List<Game> games = Arrays.asList(testGame);
        when(gameRepository.findByPlatform(platform)).thenReturn(games);

        // When
        List<GameDTO> result = gameService.findByPlatform(platform);

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(platform, result.get(0).getPlatform());
        verify(gameRepository, times(1)).findByPlatform(platform);
    }

    // ========================================
    // findTopDiscountedGames 测试
    // ========================================

    @Test
    @DisplayName("findTopDiscountedGames - 成功获取热门折扣游戏")
    void testFindTopDiscountedGames_Success() {
        // Given
        int limit = 5;
        List<Game> games = Arrays.asList(
                createTestGame(1L, "Game 1", "Dev 1", new BigDecimal("59.99")),
                createTestGame(2L, "Game 2", "Dev 2", new BigDecimal("49.99"))
        );
        when(gameRepository.findTopDiscountedGames(limit)).thenReturn(games);

        // When
        List<GameDTO> result = gameService.findTopDiscountedGames(limit);

        // Then
        assertNotNull(result);
        assertEquals(2, result.size());
        verify(gameRepository, times(1)).findTopDiscountedGames(limit);
    }

    // ========================================
    // save (Game) 测试
    // ========================================

    @Test
    @DisplayName("save - 成功保存游戏并初始化激活码")
    void testSaveGame_Success() {
        // Given
        Game savedGame = new Game();
        savedGame.setGameId(testGameId);
        savedGame.setTitle(testGame.getTitle());
        savedGame.setPrice(testGame.getPrice());
        
        when(gameRepository.save(any(Game.class))).thenReturn(savedGame);
        when(unusedRepo.countByGameId(testGameId)).thenReturn(0L);
        doNothing().when(activationCodeService).generateInitialCodes(testGameId);

        // When
        GameDTO result = gameService.save(testGame);

        // Then
        assertNotNull(result);
        assertEquals(testGameId, result.getGameId());
        verify(gameRepository, times(1)).save(any(Game.class));
        verify(unusedRepo, times(1)).countByGameId(testGameId);
        verify(activationCodeService, times(1)).generateInitialCodes(testGameId);
    }

    @Test
    @DisplayName("save - 库存充足时不生成激活码")
    void testSaveGame_StockSufficient() {
        // Given
        when(gameRepository.save(any(Game.class))).thenAnswer(invocation -> {
            Game game = invocation.getArgument(0);
            game.setGameId(testGameId);
            return game;
        });
        when(unusedRepo.countByGameId(testGameId)).thenReturn(30L);

        // When
        GameDTO result = gameService.save(testGame);

        // Then
        assertNotNull(result);
        verify(activationCodeService, never()).generateInitialCodes(anyLong());
    }

    // ========================================
    // save (GameDTO) 测试
    // ========================================

    @Test
    @DisplayName("save - 从DTO成功保存游戏")
    void testSaveGameDTO_Success() {
        // Given
        Game savedGame = new Game();
        savedGame.setGameId(testGameId);
        savedGame.setTitle(testGameDTO.getTitle());
        savedGame.setPrice(testGameDTO.getPrice());
        
        when(gameRepository.save(any(Game.class))).thenReturn(savedGame);
        when(unusedRepo.countByGameId(testGameId)).thenReturn(0L);
        doNothing().when(activationCodeService).generateInitialCodes(testGameId);

        // When
        GameDTO result = gameService.save(testGameDTO);

        // Then
        assertNotNull(result);
        assertEquals(testGameId, result.getGameId());
        verify(gameRepository, times(1)).save(any(Game.class));
        verify(activationCodeService, times(1)).generateInitialCodes(testGameId);
    }

    // ========================================
    // updateGame 测试
    // ========================================

    @Test
    @DisplayName("updateGame - 成功更新游戏")
    void testUpdateGame_Success() {
        // Given
        GameDTO updateDTO = new GameDTO();
        updateDTO.setTitle("Updated Title");
        updateDTO.setPrice(new BigDecimal("69.99"));

        when(gameRepository.findById(testGameId)).thenReturn(Optional.of(testGame));
        when(gameRepository.save(any(Game.class))).thenReturn(testGame);

        // When
        GameDTO result = gameService.updateGame(testGameId, updateDTO);

        // Then
        assertNotNull(result);
        verify(gameRepository, times(1)).findById(testGameId);
        verify(gameRepository, times(1)).save(any(Game.class));
    }

    @Test
    @DisplayName("updateGame - 游戏不存在时抛出异常")
    void testUpdateGame_NotFound() {
        // Given
        GameDTO updateDTO = new GameDTO();
        when(gameRepository.findById(testGameId)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(RuntimeException.class, () -> {
            gameService.updateGame(testGameId, updateDTO);
        });

        verify(gameRepository, never()).save(any(Game.class));
    }

    @Test
    @DisplayName("updateGame - 部分字段更新")
    void testUpdateGame_PartialUpdate() {
        // Given
        GameDTO updateDTO = new GameDTO();
        updateDTO.setTitle("Updated Title");
        // 其他字段为null，不应更新

        when(gameRepository.findById(testGameId)).thenReturn(Optional.of(testGame));
        when(gameRepository.save(any(Game.class))).thenReturn(testGame);

        // When
        GameDTO result = gameService.updateGame(testGameId, updateDTO);

        // Then
        assertNotNull(result);
        verify(gameRepository, times(1)).save(any(Game.class));
    }

    // ========================================
    // deleteGame 测试
    // ========================================

    @Test
    @DisplayName("deleteGame - 成功删除游戏")
    void testDeleteGame_Success() {
        // Given
        when(gameRepository.findById(testGameId)).thenReturn(Optional.of(testGame));
        doNothing().when(unusedRepo).deleteByGameId(testGameId);
        doNothing().when(gameRepository).delete(testGame);

        // When
        gameService.deleteGame(testGameId);

        // Then
        verify(gameRepository, times(1)).findById(testGameId);
        verify(unusedRepo, times(1)).deleteByGameId(testGameId);
        verify(gameRepository, times(1)).delete(testGame);
    }

    @Test
    @DisplayName("deleteGame - 游戏不存在时抛出异常")
    void testDeleteGame_NotFound() {
        // Given
        when(gameRepository.findById(testGameId)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(RuntimeException.class, () -> {
            gameService.deleteGame(testGameId);
        });

        verify(unusedRepo, never()).deleteByGameId(anyLong());
        verify(gameRepository, never()).delete(any(Game.class));
    }

    // ========================================
    // 辅助方法
    // ========================================

    private Game createTestGame(Long gameId, String title, String developer, BigDecimal price) {
        Game game = new Game();
        game.setGameId(gameId);
        game.setTitle(title);
        game.setDeveloper(developer);
        game.setPrice(price);
        game.setDiscountPrice(null);
        game.setGenre("RPG");
        game.setPlatform("PC");
        game.setReleaseDate(LocalDate.now());
        game.setIsActive(true);
        game.setImageUrl("http://example.com/image.jpg");
        return game;
    }

    private GameDTO createTestGameDTO(Long gameId, String title, String developer, BigDecimal price) {
        GameDTO dto = new GameDTO();
        dto.setGameId(gameId);
        dto.setTitle(title);
        dto.setDeveloper(developer);
        dto.setPrice(price);
        dto.setDiscountPrice(null);
        dto.setGenre("RPG");
        dto.setPlatform("PC");
        dto.setReleaseDate(LocalDate.now());
        dto.setIsActive(true);
        dto.setImageUrl("http://example.com/image.jpg");
        return dto;
    }
}

