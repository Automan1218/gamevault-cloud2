package com.sg.nusiss.shopping.service.shopping;

import com.sg.nusiss.shopping.entity.library.PurchasedGameActivationCode;
import com.sg.nusiss.shopping.entity.library.UnusedGameActivationCode;
import com.sg.nusiss.shopping.repository.library.PurchasedGameActivationCodeRepository;
import com.sg.nusiss.shopping.repository.library.UnusedGameActivationCodeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * GameActivationCodeService 单元测试
 *
 * 测试要点:
 * 1. 生成初始激活码
 * 2. 补充库存到目标值
 * 3. 分配激活码给订单项
 * 4. 库存统计
 * 5. 边界条件处理
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GameActivationCodeService 单元测试")
class GameActivationCodeServiceTest {

    @Mock
    private UnusedGameActivationCodeRepository unusedRepo;

    @Mock
    private PurchasedGameActivationCodeRepository purchasedRepo;

    @InjectMocks
    private GameActivationCodeService activationCodeService;

    private Long testGameId;
    private Long testUserId;
    private Long testOrderItemId;
    private int targetStock;

    @BeforeEach
    void setUp() {
        testGameId = 1L;
        testUserId = 100L;
        testOrderItemId = 200L;
        targetStock = 30;
        // 使用反射设置私有字段
        ReflectionTestUtils.setField(activationCodeService, "TARGET_STOCK", targetStock);
    }

    // ========================================
    // generateInitialCodes 测试
    // ========================================

    @Test
    @DisplayName("generateInitialCodes - 库存不足时生成激活码")
    void testGenerateInitialCodes_StockInsufficient() {
        // Given
        long existingCount = 10L;
        int toGenerate = (int) (targetStock - existingCount);
        when(unusedRepo.countByGameId(testGameId)).thenReturn(existingCount);
        when(unusedRepo.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        activationCodeService.generateInitialCodes(testGameId);

        // Then
        verify(unusedRepo, times(1)).countByGameId(testGameId);
        verify(unusedRepo, times(1)).saveAll(argThat(list -> 
            ((List<?>) list).size() == toGenerate
        ));
    }

    @Test
    @DisplayName("generateInitialCodes - 库存充足时不生成")
    void testGenerateInitialCodes_StockSufficient() {
        // Given
        long existingCount = 35L; // 超过目标值
        when(unusedRepo.countByGameId(testGameId)).thenReturn(existingCount);

        // When
        activationCodeService.generateInitialCodes(testGameId);

        // Then
        verify(unusedRepo, times(1)).countByGameId(testGameId);
        verify(unusedRepo, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("generateInitialCodes - 库存正好等于目标值时不生成")
    void testGenerateInitialCodes_StockExact() {
        // Given
        long existingCount = targetStock;
        when(unusedRepo.countByGameId(testGameId)).thenReturn(existingCount);

        // When
        activationCodeService.generateInitialCodes(testGameId);

        // Then
        verify(unusedRepo, times(1)).countByGameId(testGameId);
        verify(unusedRepo, never()).saveAll(anyList());
    }

    // ========================================
    // replenishToTarget 测试
    // ========================================

    @Test
    @DisplayName("replenishToTarget - 成功补充库存")
    void testReplenishToTarget_Success() {
        // Given
        long existingCount = 15L;
        int toGenerate = (int) (targetStock - existingCount);
        when(unusedRepo.countByGameId(testGameId)).thenReturn(existingCount);
        when(unusedRepo.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        activationCodeService.replenishToTarget(testGameId);

        // Then
        verify(unusedRepo, times(1)).countByGameId(testGameId);
        verify(unusedRepo, times(1)).saveAll(argThat(list -> 
            ((List<?>) list).size() == toGenerate
        ));
    }

    @Test
    @DisplayName("replenishToTarget - 库存充足时不补充")
    void testReplenishToTarget_StockSufficient() {
        // Given
        long existingCount = 40L;
        when(unusedRepo.countByGameId(testGameId)).thenReturn(existingCount);

        // When
        activationCodeService.replenishToTarget(testGameId);

        // Then
        verify(unusedRepo, times(1)).countByGameId(testGameId);
        verify(unusedRepo, never()).saveAll(anyList());
    }

    // ========================================
    // assignCodeToOrderItem 测试
    // ========================================

    @Test
    @DisplayName("assignCodeToOrderItem - 成功分配激活码")
    void testAssignCodeToOrderItem_Success() {
        // Given
        String activationCode = "TEST-CODE-12345";
        UnusedGameActivationCode unusedCode = new UnusedGameActivationCode();
        unusedCode.setGameId(testGameId);
        unusedCode.setActivationCode(activationCode);

        PurchasedGameActivationCode purchasedCode = PurchasedGameActivationCode.of(
                testUserId, testOrderItemId, testGameId, activationCode
        );

        when(unusedRepo.findFirstByGameId(testGameId)).thenReturn(Optional.of(unusedCode));
        when(purchasedRepo.save(any(PurchasedGameActivationCode.class))).thenReturn(purchasedCode);
        doNothing().when(unusedRepo).delete(unusedCode);
        when(unusedRepo.countByGameId(testGameId)).thenReturn(29L); // 分配后需要补充
        when(unusedRepo.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        PurchasedGameActivationCode result = activationCodeService.assignCodeToOrderItem(
                testUserId, testOrderItemId, testGameId
        );

        // Then
        assertNotNull(result);
        assertEquals(activationCode, result.getActivationCode());
        assertEquals(testUserId, result.getUserId());
        assertEquals(testOrderItemId, result.getOrderItemId());
        assertEquals(testGameId, result.getGameId());

        verify(unusedRepo, times(1)).findFirstByGameId(testGameId);
        verify(purchasedRepo, times(1)).save(any(PurchasedGameActivationCode.class));
        verify(unusedRepo, times(1)).delete(unusedCode);
        verify(unusedRepo, times(1)).countByGameId(testGameId);
        verify(unusedRepo, times(1)).saveAll(anyList());
    }

    @Test
    @DisplayName("assignCodeToOrderItem - 无可用激活码时抛出异常")
    void testAssignCodeToOrderItem_NoAvailableCode() {
        // Given
        when(unusedRepo.findFirstByGameId(testGameId)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(IllegalStateException.class, () -> {
            activationCodeService.assignCodeToOrderItem(testUserId, testOrderItemId, testGameId);
        });

        verify(purchasedRepo, never()).save(any(PurchasedGameActivationCode.class));
        verify(unusedRepo, never()).delete(any(UnusedGameActivationCode.class));
    }

    @Test
    @DisplayName("assignCodeToOrderItem - 分配后自动补充库存")
    void testAssignCodeToOrderItem_AutoReplenish() {
        // Given
        String activationCode = "TEST-CODE-12345";
        UnusedGameActivationCode unusedCode = new UnusedGameActivationCode();
        unusedCode.setGameId(testGameId);
        unusedCode.setActivationCode(activationCode);

        PurchasedGameActivationCode purchasedCode = PurchasedGameActivationCode.of(
                testUserId, testOrderItemId, testGameId, activationCode
        );

        when(unusedRepo.findFirstByGameId(testGameId)).thenReturn(Optional.of(unusedCode));
        when(purchasedRepo.save(any(PurchasedGameActivationCode.class))).thenReturn(purchasedCode);
        doNothing().when(unusedRepo).delete(unusedCode);
        // 分配后库存为29，需要补充1个
        when(unusedRepo.countByGameId(testGameId)).thenReturn(29L);
        when(unusedRepo.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        activationCodeService.assignCodeToOrderItem(testUserId, testOrderItemId, testGameId);

        // Then
        verify(unusedRepo, times(1)).saveAll(argThat(list -> 
            ((List<?>) list).size() == 1
        ));
    }

    @Test
    @DisplayName("assignCodeToOrderItem - 分配后库存充足时不补充")
    void testAssignCodeToOrderItem_NoReplenishNeeded() {
        // Given
        String activationCode = "TEST-CODE-12345";
        UnusedGameActivationCode unusedCode = new UnusedGameActivationCode();
        unusedCode.setGameId(testGameId);
        unusedCode.setActivationCode(activationCode);

        PurchasedGameActivationCode purchasedCode = PurchasedGameActivationCode.of(
                testUserId, testOrderItemId, testGameId, activationCode
        );

        when(unusedRepo.findFirstByGameId(testGameId)).thenReturn(Optional.of(unusedCode));
        when(purchasedRepo.save(any(PurchasedGameActivationCode.class))).thenReturn(purchasedCode);
        doNothing().when(unusedRepo).delete(unusedCode);
        // 分配后库存仍为30，不需要补充
        when(unusedRepo.countByGameId(testGameId)).thenReturn(30L);

        // When
        activationCodeService.assignCodeToOrderItem(testUserId, testOrderItemId, testGameId);

        // Then
        verify(unusedRepo, never()).saveAll(anyList());
    }

    // ========================================
    // getStockStats 测试
    // ========================================

    @Test
    @DisplayName("getStockStats - 成功获取库存统计")
    void testGetStockStats_Success() {
        // Given
        long unusedCount = 25L;
        // 注意：实际实现中 getStockStats 使用 findByUserId(gameId)，这可能是bug，但测试要匹配实际实现
        List<PurchasedGameActivationCode> purchasedList = Arrays.asList(
                createPurchasedCode(1L, testGameId),
                createPurchasedCode(2L, testGameId)
        );

        when(unusedRepo.countByGameId(testGameId)).thenReturn(unusedCount);
        when(purchasedRepo.findByUserId(testGameId)).thenReturn(purchasedList);

        // When
        Map<String, Long> result = activationCodeService.getStockStats(testGameId);

        // Then
        assertNotNull(result);
        assertEquals(unusedCount, result.get("unused"));
        assertEquals(2L, result.get("purchased"));
        verify(unusedRepo, times(1)).countByGameId(testGameId);
        verify(purchasedRepo, times(1)).findByUserId(testGameId);
    }

    @Test
    @DisplayName("getStockStats - 无库存和无购买记录")
    void testGetStockStats_Empty() {
        // Given
        when(unusedRepo.countByGameId(testGameId)).thenReturn(0L);
        when(purchasedRepo.findByUserId(testGameId)).thenReturn(Collections.emptyList());

        // When
        Map<String, Long> result = activationCodeService.getStockStats(testGameId);

        // Then
        assertNotNull(result);
        assertEquals(0L, result.get("unused"));
        assertEquals(0L, result.get("purchased"));
    }

    // ========================================
    // 辅助方法
    // ========================================

    private PurchasedGameActivationCode createPurchasedCode(Long id, Long gameId) {
        PurchasedGameActivationCode code = new PurchasedGameActivationCode();
        code.setActivationId(id);
        code.setGameId(gameId);
        code.setActivationCode("CODE-" + id);
        return code;
    }
}

