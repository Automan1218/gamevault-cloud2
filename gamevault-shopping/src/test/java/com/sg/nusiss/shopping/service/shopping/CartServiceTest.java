package com.sg.nusiss.shopping.service.shopping;

import com.sg.nusiss.shopping.dto.shopping.CartDTO;
import com.sg.nusiss.shopping.dto.shopping.CartItemDTO;
import com.sg.nusiss.shopping.entity.ENUM.CartStatus;
import com.sg.nusiss.shopping.entity.ENUM.OrderStatus;
import com.sg.nusiss.shopping.entity.ENUM.PaymentMethod;
import com.sg.nusiss.shopping.entity.shopping.Cart;
import com.sg.nusiss.shopping.entity.shopping.CartItem;
import com.sg.nusiss.shopping.entity.shopping.Game;
import com.sg.nusiss.shopping.entity.shopping.Order;
import com.sg.nusiss.shopping.repository.shopping.CartRepository;
import com.sg.nusiss.shopping.repository.shopping.GameRepository;
import com.sg.nusiss.shopping.repository.shopping.OrderRepository;
import com.sg.nusiss.shopping.service.discount.IDiscountStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * CartService 单元测试
 *
 * 测试要点:
 * 1. 购物车CRUD操作
 * 2. 添加/删除游戏
 * 3. 更新数量
 * 4. 清空购物车
 * 5. 计算总价
 * 6. 折扣应用
 * 7. 结账功能
 * 8. 参数验证
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CartService 单元测试")
class CartServiceTest {

    @Mock
    private CartRepository cartRepository;

    @Mock
    private GameRepository gameRepository;

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private CartService cartService;

    private Long testUserId;
    private Long testGameId;
    private Game testGame;
    private Cart testCart;

    @BeforeEach
    void setUp() {
        testUserId = 1L;
        testGameId = 100L;
        testGame = createTestGame(testGameId, "Test Game", new BigDecimal("59.99"));
        testCart = createTestCart(testUserId);
    }

    // ========================================
    // getCart 测试
    // ========================================

    @Test
    @DisplayName("getCart - 成功获取购物车")
    void testGetCart_Success() {
        // Given
        when(cartRepository.findByUserId(testUserId)).thenReturn(Optional.of(testCart));
        when(gameRepository.findAllById(anyList())).thenReturn(Arrays.asList(testGame));

        // When
        CartDTO result = cartService.getCart(testUserId);

        // Then
        assertNotNull(result);
        assertEquals(testUserId, result.getUserId());
        verify(cartRepository, times(1)).findByUserId(testUserId);
    }

    @Test
    @DisplayName("getCart - 购物车不存在时自动创建")
    void testGetCart_AutoCreate() {
        // Given
        when(cartRepository.findByUserId(testUserId)).thenReturn(Optional.empty());
        when(cartRepository.save(any(Cart.class))).thenAnswer(invocation -> {
            Cart cart = invocation.getArgument(0);
            cart.setCartId(1L);
            return cart;
        });
        when(gameRepository.findAllById(anyList())).thenReturn(Arrays.asList());

        // When
        CartDTO result = cartService.getCart(testUserId);

        // Then
        assertNotNull(result);
        assertEquals(testUserId, result.getUserId());
        verify(cartRepository, times(1)).save(any(Cart.class));
    }

    // ========================================
    // addGame 测试
    // ========================================

    @Test
    @DisplayName("addGame - 成功添加新游戏")
    void testAddGame_Success() {
        // Given
        int quantity = 2;
        when(cartRepository.findByUserId(testUserId)).thenReturn(Optional.of(testCart));
        when(gameRepository.findById(testGameId)).thenReturn(Optional.of(testGame));
        when(cartRepository.save(any(Cart.class))).thenReturn(testCart);
        when(gameRepository.findAllById(anyList())).thenReturn(Arrays.asList(testGame));

        // When
        CartDTO result = cartService.addGame(testUserId, testGameId, quantity);

        // Then
        assertNotNull(result);
        verify(gameRepository, times(1)).findById(testGameId);
        verify(cartRepository, times(1)).save(any(Cart.class));
    }

    @Test
    @DisplayName("addGame - 添加已存在的游戏时增加数量")
    void testAddGame_ExistingGame() {
        // Given
        CartItem existingItem = new CartItem(testGameId, new BigDecimal("59.99"), 1);
        existingItem.setCart(testCart);
        testCart.getCartItems().add(existingItem);

        when(cartRepository.findByUserId(testUserId)).thenReturn(Optional.of(testCart));
        when(gameRepository.findById(testGameId)).thenReturn(Optional.of(testGame));
        when(cartRepository.save(any(Cart.class))).thenReturn(testCart);
        when(gameRepository.findAllById(anyList())).thenReturn(Arrays.asList(testGame));

        // When
        CartDTO result = cartService.addGame(testUserId, testGameId, 2);

        // Then
        assertNotNull(result);
        assertEquals(3, existingItem.getQuantity()); // 1 + 2 = 3
        verify(cartRepository, times(1)).save(any(Cart.class));
    }

    @Test
    @DisplayName("addGame - 游戏不存在")
    void testAddGame_GameNotFound() {
        // Given
        when(cartRepository.findByUserId(testUserId)).thenReturn(Optional.of(testCart));
        when(gameRepository.findById(testGameId)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(java.util.NoSuchElementException.class, () -> {
            cartService.addGame(testUserId, testGameId, 1);
        });

        verify(cartRepository, never()).save(any(Cart.class));
    }

    @Test
    @DisplayName("addGame - 数量小于1时自动设为1")
    void testAddGame_QuantityLessThanOne() {
        // Given
        when(cartRepository.findByUserId(testUserId)).thenReturn(Optional.of(testCart));
        when(gameRepository.findById(testGameId)).thenReturn(Optional.of(testGame));
        when(cartRepository.save(any(Cart.class))).thenReturn(testCart);
        when(gameRepository.findAllById(anyList())).thenReturn(Arrays.asList(testGame));

        // When
        CartDTO result = cartService.addGame(testUserId, testGameId, 0);

        // Then
        assertNotNull(result);
        verify(cartRepository, times(1)).save(any(Cart.class));
    }

    // ========================================
    // removeGame 测试
    // ========================================

    @Test
    @DisplayName("removeGame - 成功删除游戏")
    void testRemoveGame_Success() {
        // Given
        CartItem item = new CartItem(testGameId, new BigDecimal("59.99"), 1);
        item.setCart(testCart);
        testCart.getCartItems().add(item);

        when(cartRepository.findByUserId(testUserId)).thenReturn(Optional.of(testCart));
        when(cartRepository.save(any(Cart.class))).thenReturn(testCart);
        when(gameRepository.findAllById(anyList())).thenReturn(Arrays.asList());

        // When
        CartDTO result = cartService.removeGame(testUserId, testGameId);

        // Then
        assertNotNull(result);
        assertTrue(testCart.getCartItems().isEmpty());
        verify(cartRepository, times(1)).save(any(Cart.class));
    }

    // ========================================
    // updateQuantity 测试
    // ========================================

    @Test
    @DisplayName("updateQuantity - 成功更新数量")
    void testUpdateQuantity_Success() {
        // Given
        CartItem item = new CartItem(testGameId, new BigDecimal("59.99"), 1);
        item.setCart(testCart);
        testCart.getCartItems().add(item);

        when(cartRepository.findByUserId(testUserId)).thenReturn(Optional.of(testCart));
        when(cartRepository.save(any(Cart.class))).thenReturn(testCart);
        when(gameRepository.findAllById(anyList())).thenReturn(Arrays.asList(testGame));

        // When
        CartDTO result = cartService.updateQuantity(testUserId, testGameId, 5);

        // Then
        assertNotNull(result);
        assertEquals(5, item.getQuantity());
        verify(cartRepository, times(1)).save(any(Cart.class));
    }

    @Test
    @DisplayName("updateQuantity - 数量小于1时抛出异常")
    void testUpdateQuantity_InvalidQuantity() {
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            cartService.updateQuantity(testUserId, testGameId, 0);
        });

        verify(cartRepository, never()).save(any(Cart.class));
    }

    @Test
    @DisplayName("updateQuantity - 游戏不在购物车中")
    void testUpdateQuantity_GameNotInCart() {
        // Given
        when(cartRepository.findByUserId(testUserId)).thenReturn(Optional.of(testCart));

        // When & Then
        assertThrows(java.util.NoSuchElementException.class, () -> {
            cartService.updateQuantity(testUserId, testGameId, 5);
        });

        verify(cartRepository, never()).save(any(Cart.class));
    }

    // ========================================
    // clearCart 测试
    // ========================================

    @Test
    @DisplayName("clearCart - 成功清空购物车")
    void testClearCart_Success() {
        // Given
        CartItem item = new CartItem(testGameId, new BigDecimal("59.99"), 1);
        item.setCart(testCart);
        testCart.getCartItems().add(item);

        when(cartRepository.findByUserId(testUserId)).thenReturn(Optional.of(testCart));
        when(cartRepository.save(any(Cart.class))).thenReturn(testCart);
        when(gameRepository.findAllById(anyList())).thenReturn(Arrays.asList());

        // When
        CartDTO result = cartService.clearCart(testUserId);

        // Then
        assertNotNull(result);
        assertTrue(testCart.getCartItems().isEmpty());
        verify(cartRepository, times(1)).save(any(Cart.class));
    }

    // ========================================
    // calculateTotalAmount 测试
    // ========================================

    @Test
    @DisplayName("calculateTotalAmount - 成功计算总价")
    void testCalculateTotalAmount_Success() {
        // Given
        BigDecimal expectedTotal = new BigDecimal("119.98");
        when(cartRepository.sumTotalByUserId(testUserId)).thenReturn(expectedTotal);

        // When
        BigDecimal result = cartService.calculateTotalAmount(testUserId);

        // Then
        assertNotNull(result);
        assertEquals(expectedTotal, result);
        verify(cartRepository, times(1)).sumTotalByUserId(testUserId);
    }

    // ========================================
    // applyDiscounts 测试
    // ========================================

    @Test
    @DisplayName("applyDiscounts - 成功应用折扣")
    void testApplyDiscounts_Success() {
        // Given
        CartItem item = new CartItem(testGameId, new BigDecimal("59.99"), 1);
        item.setCart(testCart);
        testCart.getCartItems().add(item);

        IDiscountStrategy discountStrategy = mock(IDiscountStrategy.class);
        when(discountStrategy.isApplicable(testGame)).thenReturn(true);
        when(discountStrategy.calculateDiscount(eq(testGame), any(BigDecimal.class)))
                .thenReturn(new BigDecimal("10.00"));

        cartService.setDiscountStrategy(discountStrategy);

        when(cartRepository.findByUserId(testUserId)).thenReturn(Optional.of(testCart));
        when(gameRepository.findById(testGameId)).thenReturn(Optional.of(testGame));
        when(cartRepository.save(any(Cart.class))).thenReturn(testCart);

        // When
        boolean result = cartService.applyDiscounts(testUserId);

        // Then
        assertTrue(result);
        verify(cartRepository, times(1)).save(any(Cart.class));
    }

    @Test
    @DisplayName("applyDiscounts - 无折扣策略")
    void testApplyDiscounts_NoDiscount() {
        // Given
        CartItem item = new CartItem(testGameId, new BigDecimal("59.99"), 1);
        item.setCart(testCart);
        testCart.getCartItems().add(item);

        when(cartRepository.findByUserId(testUserId)).thenReturn(Optional.of(testCart));
        when(gameRepository.findById(testGameId)).thenReturn(Optional.of(testGame));
        when(cartRepository.save(any(Cart.class))).thenReturn(testCart);

        // When
        boolean result = cartService.applyDiscounts(testUserId);

        // Then
        assertFalse(result);
    }

    // ========================================
    // checkout 测试
    // ========================================

    @Test
    @DisplayName("checkout - 成功结账")
    void testCheckout_Success() {
        // Given
        CartItem item = new CartItem(testGameId, new BigDecimal("59.99"), 2);
        item.setCart(testCart);
        testCart.getCartItems().add(item);

        Order savedOrder = new Order();
        savedOrder.setOrderId(1L);
        savedOrder.setUserId(testUserId);
        savedOrder.setStatus(OrderStatus.PENDING);
        savedOrder.setPaymentMethod(PaymentMethod.CREDIT_CARD);
        savedOrder.setFinalAmount(new BigDecimal("119.98"));

        when(cartRepository.findByUserId(testUserId)).thenReturn(Optional.of(testCart));
        when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);
        when(cartRepository.save(any(Cart.class))).thenReturn(testCart);

        // When
        var result = cartService.checkout(testUserId, PaymentMethod.CREDIT_CARD);

        // Then
        assertNotNull(result);
        assertEquals(testUserId, result.getUserId());
        assertTrue(testCart.getCartItems().isEmpty());
        verify(orderRepository, times(1)).save(any(Order.class));
        verify(cartRepository, times(1)).save(any(Cart.class));
    }

    @Test
    @DisplayName("checkout - 购物车为空时抛出异常")
    void testCheckout_EmptyCart() {
        // Given
        when(cartRepository.findByUserId(testUserId)).thenReturn(Optional.of(testCart));

        // When & Then
        assertThrows(IllegalStateException.class, () -> {
            cartService.checkout(testUserId, PaymentMethod.CREDIT_CARD);
        });

        verify(orderRepository, never()).save(any(Order.class));
    }

    // ========================================
    // calculateFinalAmount 测试
    // ========================================

    @Test
    @DisplayName("calculateFinalAmount - 成功计算最终金额")
    void testCalculateFinalAmount_Success() {
        // Given
        CartItem item = new CartItem(testGameId, new BigDecimal("59.99"), 1);
        item.setCart(testCart);
        testCart.getCartItems().add(item);
        testCart.setDiscountAmount(new BigDecimal("10.00"));

        when(cartRepository.findByUserId(testUserId)).thenReturn(Optional.of(testCart));

        // When
        BigDecimal result = cartService.calculateFinalAmount(testUserId);

        // Then
        assertNotNull(result);
        assertEquals(new BigDecimal("49.99"), result);
    }

    // ========================================
    // markCheckedOut 测试
    // ========================================

    @Test
    @DisplayName("markCheckedOut - 成功标记为已结账")
    void testMarkCheckedOut_Success() {
        // Given
        CartItem item = new CartItem(testGameId, new BigDecimal("59.99"), 1);
        item.setCart(testCart);
        testCart.getCartItems().add(item);

        when(cartRepository.findByUserId(testUserId)).thenReturn(Optional.of(testCart));
        when(cartRepository.save(any(Cart.class))).thenReturn(testCart);

        // When
        cartService.markCheckedOut(testUserId, PaymentMethod.CREDIT_CARD);

        // Then
        assertEquals(CartStatus.CHECKED_OUT, testCart.getStatus());
        assertEquals(PaymentMethod.CREDIT_CARD, testCart.getPaymentMethod());
        assertTrue(testCart.getCartItems().isEmpty());
        verify(cartRepository, times(1)).save(any(Cart.class));
    }

    @Test
    @DisplayName("markCheckedOut - 购物车为空时抛出异常")
    void testMarkCheckedOut_EmptyCart() {
        // Given
        when(cartRepository.findByUserId(testUserId)).thenReturn(Optional.of(testCart));

        // When & Then
        assertThrows(IllegalStateException.class, () -> {
            cartService.markCheckedOut(testUserId, PaymentMethod.CREDIT_CARD);
        });
    }

    // ========================================
    // 辅助方法
    // ========================================

    private Game createTestGame(Long gameId, String title, BigDecimal price) {
        Game game = new Game();
        game.setGameId(gameId);
        game.setTitle(title);
        game.setPrice(price);
        game.setDiscountPrice(null); // 没有折扣，所以 getCurrentPrice() 会返回 price
        game.setIsActive(true);
        return game;
    }

    private Cart createTestCart(Long userId) {
        Cart cart = new Cart();
        cart.setCartId(1L);
        cart.setUserId(userId);
        cart.setStatus(CartStatus.ACTIVE);
        cart.setCartItems(new ArrayList<>());
        cart.setCreatedDate(LocalDateTime.now());
        cart.setLastModifiedDate(LocalDateTime.now());
        return cart;
    }
}

