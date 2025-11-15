package com.sg.nusiss.shopping.service.shopping;

import com.sg.nusiss.shopping.dto.library.OrderDTO;
import com.sg.nusiss.shopping.entity.ENUM.OrderStatus;
import com.sg.nusiss.shopping.entity.ENUM.PaymentMethod;
import com.sg.nusiss.shopping.entity.library.PurchasedGameActivationCode;
import com.sg.nusiss.shopping.entity.shopping.Cart;
import com.sg.nusiss.shopping.entity.shopping.CartItem;
import com.sg.nusiss.shopping.entity.shopping.Game;
import com.sg.nusiss.shopping.entity.shopping.Order;
import com.sg.nusiss.shopping.entity.shopping.OrderItem;
import com.sg.nusiss.shopping.repository.library.PurchasedGameActivationCodeRepository;
import com.sg.nusiss.shopping.repository.shopping.GameRepository;
import com.sg.nusiss.shopping.repository.shopping.OrderRepository;
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
 * OrderService 单元测试
 *
 * 测试要点:
 * 1. 创建待支付订单
 * 2. 支付成功并分配激活码
 * 3. 支付失败标记
 * 4. 订单查询
 * 5. 参数验证
 * 6. 权限验证
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OrderService 单元测试")
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private PurchasedGameActivationCodeRepository purchasedRepo;

    @Mock
    private GameActivationCodeService codeService;

    @Mock
    private GameRepository gameRepository;

    @InjectMocks
    private OrderService orderService;

    private Long testUserId;
    private Long testOrderId;
    private Long testGameId;
    private Cart testCart;
    private Order testOrder;
    private Game testGame;

    @BeforeEach
    void setUp() {
        testUserId = 1L;
        testOrderId = 100L;
        testGameId = 200L;
        testCart = createTestCart(testUserId);
        testOrder = createTestOrder(testOrderId, testUserId);
        testGame = createTestGame(testGameId, "Test Game", new BigDecimal("59.99"));
    }

    // ========================================
    // createPendingOrder 测试
    // ========================================

    @Test
    @DisplayName("createPendingOrder - 成功创建待支付订单")
    void testCreatePendingOrder_Success() {
        // Given
        CartItem item = new CartItem(testGameId, new BigDecimal("59.99"), 2);
        item.setCart(testCart);
        testCart.getCartItems().add(item);

        when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            order.setOrderId(testOrderId);
            return order;
        });

        // When
        OrderDTO result = orderService.createPendingOrder(testCart, PaymentMethod.CREDIT_CARD);

        // Then
        assertNotNull(result);
        assertEquals(testUserId, result.getUserId());
        assertEquals(OrderStatus.PENDING.name(), result.getStatus());
        assertEquals(PaymentMethod.CREDIT_CARD.name(), result.getPaymentMethod());
        verify(orderRepository, times(1)).saveAndFlush(any(Order.class));
    }

    @Test
    @DisplayName("createPendingOrder - 购物车为空时抛出异常")
    void testCreatePendingOrder_EmptyCart() {
        // Given
        Cart emptyCart = new Cart(testUserId);

        // When & Then
        assertThrows(IllegalStateException.class, () -> {
            orderService.createPendingOrder(emptyCart, PaymentMethod.CREDIT_CARD);
        });

        verify(orderRepository, never()).saveAndFlush(any(Order.class));
    }

    @Test
    @DisplayName("createPendingOrder - 正确计算订单总价")
    void testCreatePendingOrder_CalculateTotal() {
        // Given
        CartItem item1 = new CartItem(testGameId, new BigDecimal("59.99"), 2);
        CartItem item2 = new CartItem(300L, new BigDecimal("29.99"), 1);
        item1.setCart(testCart);
        item2.setCart(testCart);
        testCart.getCartItems().add(item1);
        testCart.getCartItems().add(item2);

        when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            order.setOrderId(testOrderId);
            return order;
        });

        // When
        OrderDTO result = orderService.createPendingOrder(testCart, PaymentMethod.CREDIT_CARD);

        // Then
        assertNotNull(result);
        // 总价 = 59.99 * 2 + 29.99 * 1 = 149.97
        assertEquals(new BigDecimal("149.97"), result.getFinalAmount());
    }

    // ========================================
    // captureAndFulfill 测试
    // ========================================

    @Test
    @DisplayName("captureAndFulfill - 成功支付并分配激活码")
    void testCaptureAndFulfill_Success() {
        // Given
        OrderItem orderItem = new OrderItem();
        orderItem.setOrderItemId(1L);
        orderItem.setGameId(testGameId);
        orderItem.setOrderStatus(OrderStatus.PENDING);
        orderItem.setOrder(testOrder);
        testOrder.getOrderItems().add(orderItem);

        PurchasedGameActivationCode purchasedCode = new PurchasedGameActivationCode();
        purchasedCode.setActivationCode("TEST-CODE-123");

        when(orderRepository.findById(testOrderId)).thenReturn(Optional.of(testOrder));
        when(codeService.assignCodeToOrderItem(testUserId, 1L, testGameId))
                .thenReturn(purchasedCode);
        when(gameRepository.findAllById(anyList())).thenReturn(Arrays.asList(testGame));
        when(purchasedRepo.findByOrderItemId(1L)).thenReturn(Optional.of(purchasedCode));

        // When
        OrderDTO result = orderService.captureAndFulfill(testOrderId, testUserId);

        // Then
        assertNotNull(result);
        assertEquals(OrderStatus.COMPLETED.name(), result.getStatus());
        verify(codeService, times(1)).assignCodeToOrderItem(testUserId, 1L, testGameId);
    }

    @Test
    @DisplayName("captureAndFulfill - 订单不存在时抛出异常")
    void testCaptureAndFulfill_OrderNotFound() {
        // Given
        when(orderRepository.findById(testOrderId)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            orderService.captureAndFulfill(testOrderId, testUserId);
        });

        verify(codeService, never()).assignCodeToOrderItem(anyLong(), anyLong(), anyLong());
    }

    @Test
    @DisplayName("captureAndFulfill - 用户无权限时抛出异常")
    void testCaptureAndFulfill_Unauthorized() {
        // Given
        Long otherUserId = 999L;
        when(orderRepository.findById(testOrderId)).thenReturn(Optional.of(testOrder));

        // When & Then
        assertThrows(IllegalStateException.class, () -> {
            orderService.captureAndFulfill(testOrderId, otherUserId);
        });

        verify(codeService, never()).assignCodeToOrderItem(anyLong(), anyLong(), anyLong());
    }

    @Test
    @DisplayName("captureAndFulfill - 已完成的订单幂等处理")
    void testCaptureAndFulfill_AlreadyCompleted() {
        // Given
        testOrder.setStatus(OrderStatus.COMPLETED);
        when(orderRepository.findById(testOrderId)).thenReturn(Optional.of(testOrder));
        when(gameRepository.findAllById(anyList())).thenReturn(Arrays.asList(testGame));
        // 注意：由于订单已完成，不会调用 purchasedRepo.findByOrderItemId，所以不需要 stubbing

        // When
        OrderDTO result = orderService.captureAndFulfill(testOrderId, testUserId);

        // Then
        assertNotNull(result);
        assertEquals(OrderStatus.COMPLETED.name(), result.getStatus());
        verify(codeService, never()).assignCodeToOrderItem(anyLong(), anyLong(), anyLong());
    }

    // ========================================
    // markFailed 测试
    // ========================================

    @Test
    @DisplayName("markFailed - 成功标记支付失败")
    void testMarkFailed_Success() {
        // Given
        OrderItem orderItem = new OrderItem();
        orderItem.setOrderItemId(1L);
        orderItem.setOrderStatus(OrderStatus.PENDING);
        orderItem.setOrder(testOrder);
        testOrder.getOrderItems().add(orderItem);

        when(orderRepository.findById(testOrderId)).thenReturn(Optional.of(testOrder));

        // When
        orderService.markFailed(testOrderId, testUserId);

        // Then
        assertEquals(OrderStatus.CANCELLED, testOrder.getStatus());
        assertEquals(OrderStatus.CANCELLED, orderItem.getOrderStatus());
    }

    @Test
    @DisplayName("markFailed - 订单不存在时抛出异常")
    void testMarkFailed_OrderNotFound() {
        // Given
        when(orderRepository.findById(testOrderId)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(java.util.NoSuchElementException.class, () -> {
            orderService.markFailed(testOrderId, testUserId);
        });
    }

    @Test
    @DisplayName("markFailed - 用户无权限时抛出异常")
    void testMarkFailed_Unauthorized() {
        // Given
        Long otherUserId = 999L;
        when(orderRepository.findById(testOrderId)).thenReturn(Optional.of(testOrder));

        // When & Then
        assertThrows(IllegalStateException.class, () -> {
            orderService.markFailed(testOrderId, otherUserId);
        });
    }

    @Test
    @DisplayName("markFailed - 已完成的订单不能取消")
    void testMarkFailed_AlreadyCompleted() {
        // Given
        testOrder.setStatus(OrderStatus.COMPLETED);
        when(orderRepository.findById(testOrderId)).thenReturn(Optional.of(testOrder));

        // When
        orderService.markFailed(testOrderId, testUserId);

        // Then
        assertEquals(OrderStatus.COMPLETED, testOrder.getStatus());
    }

    // ========================================
    // findById 测试
    // ========================================

    @Test
    @DisplayName("findById - 成功获取订单")
    void testFindById_Success() {
        // Given
        OrderItem orderItem = new OrderItem();
        orderItem.setOrderItemId(1L);
        orderItem.setGameId(testGameId);
        orderItem.setOrderStatus(OrderStatus.PENDING);
        orderItem.setOrder(testOrder);
        testOrder.getOrderItems().add(orderItem);

        when(orderRepository.findById(testOrderId)).thenReturn(Optional.of(testOrder));
        when(gameRepository.findAllById(anyList())).thenReturn(Arrays.asList(testGame));
        when(purchasedRepo.findByOrderItemId(1L)).thenReturn(Optional.empty());

        // When
        Optional<OrderDTO> result = orderService.findById(testOrderId);

        // Then
        assertTrue(result.isPresent());
        assertEquals(testOrderId, result.get().getOrderId());
        verify(orderRepository, times(1)).findById(testOrderId);
    }

    @Test
    @DisplayName("findById - 订单不存在")
    void testFindById_NotFound() {
        // Given
        when(orderRepository.findById(testOrderId)).thenReturn(Optional.empty());

        // When
        Optional<OrderDTO> result = orderService.findById(testOrderId);

        // Then
        assertFalse(result.isPresent());
    }

    // ========================================
    // findByUserId 测试
    // ========================================

    @Test
    @DisplayName("findByUserId - 成功获取用户订单列表")
    void testFindByUserId_Success() {
        // Given
        Order order1 = createTestOrder(1L, testUserId);
        Order order2 = createTestOrder(2L, testUserId);
        List<Order> orders = Arrays.asList(order1, order2);

        when(orderRepository.findByUserIdOrderByOrderIdDesc(testUserId)).thenReturn(orders);
        when(gameRepository.findAllById(anyList())).thenReturn(Arrays.asList(testGame));
        // 注意：如果订单项为空，不会调用 purchasedRepo.findByOrderItemId，所以不需要 stubbing

        // When
        List<OrderDTO> result = orderService.findByUserId(testUserId);

        // Then
        assertNotNull(result);
        assertEquals(2, result.size());
        verify(orderRepository, times(1)).findByUserIdOrderByOrderIdDesc(testUserId);
    }

    @Test
    @DisplayName("findByUserId - 空列表")
    void testFindByUserId_EmptyList() {
        // Given
        when(orderRepository.findByUserIdOrderByOrderIdDesc(testUserId)).thenReturn(Arrays.asList());

        // When
        List<OrderDTO> result = orderService.findByUserId(testUserId);

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // ========================================
    // 辅助方法
    // ========================================

    private Cart createTestCart(Long userId) {
        Cart cart = new Cart(userId);
        cart.setCartId(1L);
        cart.setCartItems(new ArrayList<>());
        return cart;
    }

    private Order createTestOrder(Long orderId, Long userId) {
        Order order = new Order();
        order.setOrderId(orderId);
        order.setUserId(userId);
        order.setStatus(OrderStatus.PENDING);
        order.setPaymentMethod(PaymentMethod.CREDIT_CARD);
        order.setOrderDate(LocalDateTime.now());
        order.setFinalAmount(new BigDecimal("59.99"));
        order.setOrderItems(new ArrayList<>());
        return order;
    }

    private Game createTestGame(Long gameId, String title, BigDecimal price) {
        Game game = new Game();
        game.setGameId(gameId);
        game.setTitle(title);
        game.setPrice(price);
        game.setIsActive(true);
        return game;
    }
}

