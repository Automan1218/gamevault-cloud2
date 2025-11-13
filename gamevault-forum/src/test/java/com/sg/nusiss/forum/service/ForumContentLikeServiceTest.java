package com.sg.nusiss.forum.service;

import com.sg.nusiss.forum.constant.ForumRelationType;
import com.sg.nusiss.forum.entity.UserContentRelation;
import com.sg.nusiss.forum.repository.ForumContentLikeMapper;
import com.sg.nusiss.forum.repository.ForumMetricMapper;
import com.sg.nusiss.forum.service.forum.ForumContentLikeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ForumContentLikeService 单元测试
 *
 * 测试要点:
 * 1. 点赞/取消点赞操作
 * 2. 点赞状态查询
 * 3. 批量操作
 * 4. 参数验证
 * 5. 边界条件
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ForumContentLikeService 单元测试")
class ForumContentLikeServiceTest {

    @Mock
    private ForumContentLikeMapper contentLikeMapper;

    @Mock
    private ForumMetricMapper metricMapper;

    @InjectMocks
    private ForumContentLikeService contentLikeService;

    private Long testContentId;
    private Long testUserId;
    private int likeRelationType;

    @BeforeEach
    void setUp() {
        testContentId = 1L;
        testUserId = 100L;
        likeRelationType = ForumRelationType.LIKE.intValue();
    }

    // ========================================
    // likeContent 测试
    // ========================================

    @Test
    @DisplayName("likeContent - 成功点赞")
    void testLikeContent_Success() {
        // Given
        when(contentLikeMapper.existsByUserAndContentAndType(testUserId, testContentId, likeRelationType))
                .thenReturn(false);
        when(contentLikeMapper.insert(any(UserContentRelation.class))).thenReturn(1);

        // When
        boolean result = contentLikeService.likeContent(testContentId, testUserId);

        // Then
        assertTrue(result);
        verify(contentLikeMapper, times(1)).existsByUserAndContentAndType(testUserId, testContentId, likeRelationType);
        verify(contentLikeMapper, times(1)).insert(any(UserContentRelation.class));
    }

    @Test
    @DisplayName("likeContent - 已经点赞过")
    void testLikeContent_AlreadyLiked() {
        // Given
        when(contentLikeMapper.existsByUserAndContentAndType(testUserId, testContentId, likeRelationType))
                .thenReturn(true);

        // When
        boolean result = contentLikeService.likeContent(testContentId, testUserId);

        // Then
        assertFalse(result);
        verify(contentLikeMapper, times(1)).existsByUserAndContentAndType(testUserId, testContentId, likeRelationType);
        verify(contentLikeMapper, never()).insert(any(UserContentRelation.class));
    }

    @Test
    @DisplayName("likeContent - contentId为null")
    void testLikeContent_NullContentId() {
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            contentLikeService.likeContent(null, testUserId);
        });

        verify(contentLikeMapper, never()).insert(any());
    }

    @Test
    @DisplayName("likeContent - userId为null")
    void testLikeContent_NullUserId() {
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            contentLikeService.likeContent(testContentId, null);
        });

        verify(contentLikeMapper, never()).insert(any());
    }

    @Test
    @DisplayName("likeContent - 数据库插入失败")
    void testLikeContent_InsertFailed() {
        // Given
        when(contentLikeMapper.existsByUserAndContentAndType(testUserId, testContentId, likeRelationType))
                .thenReturn(false);
        when(contentLikeMapper.insert(any(UserContentRelation.class))).thenReturn(0);

        // When
        boolean result = contentLikeService.likeContent(testContentId, testUserId);

        // Then
        assertFalse(result);
    }

    // ========================================
    // unlikeContent 测试
    // ========================================

    @Test
    @DisplayName("unlikeContent - 成功取消点赞")
    void testUnlikeContent_Success() {
        // Given
        when(contentLikeMapper.existsByUserAndContentAndType(testUserId, testContentId, likeRelationType))
                .thenReturn(true);
        when(contentLikeMapper.deleteByUserAndContentAndType(testUserId, testContentId, likeRelationType))
                .thenReturn(1);

        // When
        boolean result = contentLikeService.unlikeContent(testContentId, testUserId);

        // Then
        assertTrue(result);
        verify(contentLikeMapper, times(1)).deleteByUserAndContentAndType(testUserId, testContentId, likeRelationType);
    }

    @Test
    @DisplayName("unlikeContent - 未点赞过")
    void testUnlikeContent_NotLiked() {
        // Given
        when(contentLikeMapper.existsByUserAndContentAndType(testUserId, testContentId, likeRelationType))
                .thenReturn(false);

        // When
        boolean result = contentLikeService.unlikeContent(testContentId, testUserId);

        // Then
        assertFalse(result);
        verify(contentLikeMapper, never()).deleteByUserAndContentAndType(anyLong(), anyLong(), anyInt());
    }

    @Test
    @DisplayName("unlikeContent - contentId为null")
    void testUnlikeContent_NullContentId() {
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            contentLikeService.unlikeContent(null, testUserId);
        });
    }

    @Test
    @DisplayName("unlikeContent - userId为null")
    void testUnlikeContent_NullUserId() {
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            contentLikeService.unlikeContent(testContentId, null);
        });
    }

    // ========================================
    // toggleLike 测试
    // ========================================

    @Test
    @DisplayName("toggleLike - 从未点赞变为点赞")
    void testToggleLike_LikeToUnlike() {
        // Given
        when(contentLikeMapper.existsByUserAndContentAndType(testUserId, testContentId, likeRelationType))
                .thenReturn(false);
        when(contentLikeMapper.insert(any(UserContentRelation.class))).thenReturn(1);

        // When
        boolean result = contentLikeService.toggleLike(testContentId, testUserId);

        // Then
        assertTrue(result); // 点赞成功返回 true
        verify(contentLikeMapper, times(1)).insert(any(UserContentRelation.class));
    }

    @Test
    @DisplayName("toggleLike - 从点赞变为取消点赞")
    void testToggleLike_UnlikeToLike() {
        // Given
        when(contentLikeMapper.existsByUserAndContentAndType(testUserId, testContentId, likeRelationType))
                .thenReturn(true);
        when(contentLikeMapper.deleteByUserAndContentAndType(testUserId, testContentId, likeRelationType))
                .thenReturn(1);

        // When
        boolean result = contentLikeService.toggleLike(testContentId, testUserId);

        // Then
        assertFalse(result); // 取消点赞返回 false
        verify(contentLikeMapper, times(1)).deleteByUserAndContentAndType(testUserId, testContentId, likeRelationType);
    }

    // ========================================
    // isLiked 测试
    // ========================================

    @Test
    @DisplayName("isLiked - 已点赞")
    void testIsLiked_True() {
        // Given
        when(contentLikeMapper.existsByUserAndContentAndType(testUserId, testContentId, likeRelationType))
                .thenReturn(true);

        // When
        boolean result = contentLikeService.isLiked(testContentId, testUserId);

        // Then
        assertTrue(result);
    }

    @Test
    @DisplayName("isLiked - 未点赞")
    void testIsLiked_False() {
        // Given
        when(contentLikeMapper.existsByUserAndContentAndType(testUserId, testContentId, likeRelationType))
                .thenReturn(false);

        // When
        boolean result = contentLikeService.isLiked(testContentId, testUserId);

        // Then
        assertFalse(result);
    }

    @Test
    @DisplayName("isLiked - contentId为null")
    void testIsLiked_NullContentId() {
        // When
        boolean result = contentLikeService.isLiked(null, testUserId);

        // Then
        assertFalse(result);
        verify(contentLikeMapper, never()).existsByUserAndContentAndType(anyLong(), anyLong(), anyInt());
    }

    @Test
    @DisplayName("isLiked - userId为null")
    void testIsLiked_NullUserId() {
        // When
        boolean result = contentLikeService.isLiked(testContentId, null);

        // Then
        assertFalse(result);
        verify(contentLikeMapper, never()).existsByUserAndContentAndType(anyLong(), anyLong(), anyInt());
    }

    // ========================================
    // getLikeCount 测试
    // ========================================

    @Test
    @DisplayName("getLikeCount - 成功获取点赞数")
    void testGetLikeCount_Success() {
        // Given
        when(contentLikeMapper.countByContentAndType(testContentId, likeRelationType)).thenReturn(10);

        // When
        int count = contentLikeService.getLikeCount(testContentId);

        // Then
        assertEquals(10, count);
        verify(contentLikeMapper, times(1)).countByContentAndType(testContentId, likeRelationType);
    }

    @Test
    @DisplayName("getLikeCount - contentId为null")
    void testGetLikeCount_NullContentId() {
        // When
        int count = contentLikeService.getLikeCount(null);

        // Then
        assertEquals(0, count);
        verify(contentLikeMapper, never()).countByContentAndType(anyLong(), anyInt());
    }

    @Test
    @DisplayName("getLikeCount - 无点赞")
    void testGetLikeCount_NoLikes() {
        // Given
        when(contentLikeMapper.countByContentAndType(testContentId, likeRelationType)).thenReturn(0);

        // When
        int count = contentLikeService.getLikeCount(testContentId);

        // Then
        assertEquals(0, count);
    }

    // ========================================
    // getLikedUserIds 测试
    // ========================================

    @Test
    @DisplayName("getLikedUserIds - 成功获取点赞用户列表")
    void testGetLikedUserIds_Success() {
        // Given
        List<Long> userIds = Arrays.asList(1L, 2L, 3L);
        when(contentLikeMapper.findUserIdsByContentAndType(testContentId, likeRelationType))
                .thenReturn(userIds);

        // When
        List<Long> result = contentLikeService.getLikedUserIds(testContentId);

        // Then
        assertNotNull(result);
        assertEquals(3, result.size());
        assertTrue(result.contains(1L));
        assertTrue(result.contains(2L));
        assertTrue(result.contains(3L));
    }

    @Test
    @DisplayName("getLikedUserIds - contentId为null")
    void testGetLikedUserIds_NullContentId() {
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            contentLikeService.getLikedUserIds(null);
        });
    }

    // ========================================
    // getUserLikedContentIds 测试
    // ========================================

    @Test
    @DisplayName("getUserLikedContentIds - 成功获取用户点赞的内容列表")
    void testGetUserLikedContentIds_Success() {
        // Given
        List<Long> contentIds = Arrays.asList(10L, 20L, 30L);
        when(contentLikeMapper.findContentIdsByUserAndType(testUserId, likeRelationType))
                .thenReturn(contentIds);

        // When
        List<Long> result = contentLikeService.getUserLikedContentIds(testUserId);

        // Then
        assertNotNull(result);
        assertEquals(3, result.size());
        assertTrue(result.contains(10L));
        assertTrue(result.contains(20L));
        assertTrue(result.contains(30L));
    }

    @Test
    @DisplayName("getUserLikedContentIds - userId为null")
    void testGetUserLikedContentIds_NullUserId() {
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            contentLikeService.getUserLikedContentIds(null);
        });
    }

    // ========================================
    // batchCheckLikeStatus 测试
    // ========================================

    @Test
    @DisplayName("batchCheckLikeStatus - 成功批量查询点赞状态")
    void testBatchCheckLikeStatus_Success() {
        // Given
        List<Long> contentIds = Arrays.asList(1L, 2L, 3L, 4L);
        List<Long> likedIds = Arrays.asList(1L, 3L);

        when(contentLikeMapper.findLikedContentIdsByUserAndType(testUserId, contentIds, likeRelationType))
                .thenReturn(likedIds);

        // When
        Map<Long, Boolean> result = contentLikeService.batchCheckLikeStatus(testUserId, contentIds);

        // Then
        assertNotNull(result);
        assertEquals(4, result.size());
        assertTrue(result.get(1L));
        assertFalse(result.get(2L));
        assertTrue(result.get(3L));
        assertFalse(result.get(4L));
    }

    @Test
    @DisplayName("batchCheckLikeStatus - userId为null")
    void testBatchCheckLikeStatus_NullUserId() {
        // When
        Map<Long, Boolean> result = contentLikeService.batchCheckLikeStatus(null, Arrays.asList(1L, 2L));

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("batchCheckLikeStatus - contentIds为空")
    void testBatchCheckLikeStatus_EmptyContentIds() {
        // When
        Map<Long, Boolean> result = contentLikeService.batchCheckLikeStatus(testUserId, Arrays.asList());

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("batchCheckLikeStatus - contentIds为null")
    void testBatchCheckLikeStatus_NullContentIds() {
        // When
        Map<Long, Boolean> result = contentLikeService.batchCheckLikeStatus(testUserId, null);

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // ========================================
    // batchGetLikeCounts 测试
    // ========================================

    @Test
    @DisplayName("batchGetLikeCounts - 成功批量获取点赞数")
    void testBatchGetLikeCounts_Success() {
        // Given
        List<Long> contentIds = Arrays.asList(1L, 2L, 3L);
        Map<Long, Integer> expectedCounts = new HashMap<>();
        expectedCounts.put(1L, 10);
        expectedCounts.put(2L, 20);
        expectedCounts.put(3L, 30);

        when(metricMapper.getBatchMetrics(contentIds, "like_count")).thenReturn(expectedCounts);

        // When
        Map<Long, Integer> result = contentLikeService.batchGetLikeCounts(contentIds);

        // Then
        assertNotNull(result);
        assertEquals(3, result.size());
        assertEquals(10, result.get(1L));
        assertEquals(20, result.get(2L));
        assertEquals(30, result.get(3L));
    }

    @Test
    @DisplayName("batchGetLikeCounts - contentIds为空")
    void testBatchGetLikeCounts_EmptyContentIds() {
        // When
        Map<Long, Integer> result = contentLikeService.batchGetLikeCounts(Arrays.asList());

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("batchGetLikeCounts - contentIds为null")
    void testBatchGetLikeCounts_NullContentIds() {
        // When
        Map<Long, Integer> result = contentLikeService.batchGetLikeCounts(null);

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // ========================================
    // getUserRecentLikes 测试
    // ========================================

    @Test
    @DisplayName("getUserRecentLikes - 成功获取用户最近点赞")
    void testGetUserRecentLikes_Success() {
        // Given
        List<UserContentRelation> relations = Arrays.asList(
                createRelation(1L, testUserId, 10L),
                createRelation(2L, testUserId, 20L)
        );

        when(contentLikeMapper.findRecentByUserAndType(testUserId, likeRelationType, 10))
                .thenReturn(relations);

        // When
        List<UserContentRelation> result = contentLikeService.getUserRecentLikes(testUserId, 10);

        // Then
        assertNotNull(result);
        assertEquals(2, result.size());
    }

    @Test
    @DisplayName("getUserRecentLikes - userId为null")
    void testGetUserRecentLikes_NullUserId() {
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            contentLikeService.getUserRecentLikes(null, 10);
        });
    }

    // ========================================
    // getContentRecentLikes 测试
    // ========================================

    @Test
    @DisplayName("getContentRecentLikes - 成功获取内容最近点赞")
    void testGetContentRecentLikes_Success() {
        // Given
        List<UserContentRelation> relations = Arrays.asList(
                createRelation(1L, 100L, testContentId),
                createRelation(2L, 200L, testContentId)
        );

        when(contentLikeMapper.findRecentByContentAndType(testContentId, likeRelationType, 10))
                .thenReturn(relations);

        // When
        List<UserContentRelation> result = contentLikeService.getContentRecentLikes(testContentId, 10);

        // Then
        assertNotNull(result);
        assertEquals(2, result.size());
    }

    @Test
    @DisplayName("getContentRecentLikes - contentId为null")
    void testGetContentRecentLikes_NullContentId() {
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            contentLikeService.getContentRecentLikes(null, 10);
        });
    }

    // ========================================
    // syncLikeCount 测试
    // ========================================

    @Test
    @DisplayName("syncLikeCount - 成功同步点赞数")
    void testSyncLikeCount_Success() {
        // Given
        when(contentLikeMapper.countByContentAndType(testContentId, likeRelationType)).thenReturn(15);

        // When
        contentLikeService.syncLikeCount(testContentId);

        // Then
        verify(contentLikeMapper, times(1)).countByContentAndType(testContentId, likeRelationType);
        verify(metricMapper, times(1)).setMetricValue(testContentId, "like_count", 15);
    }

    @Test
    @DisplayName("syncLikeCount - contentId为null")
    void testSyncLikeCount_NullContentId() {
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            contentLikeService.syncLikeCount(null);
        });
    }

    // ========================================
    // batchSyncLikeCounts 测试
    // ========================================

    @Test
    @DisplayName("batchSyncLikeCounts - 成功批量同步点赞数")
    void testBatchSyncLikeCounts_Success() {
        // Given
        List<Long> contentIds = Arrays.asList(1L, 2L, 3L);
        when(contentLikeMapper.countByContentAndType(anyLong(), eq(likeRelationType))).thenReturn(10);

        // When
        contentLikeService.batchSyncLikeCounts(contentIds);

        // Then
        verify(contentLikeMapper, times(3)).countByContentAndType(anyLong(), eq(likeRelationType));
        verify(metricMapper, times(3)).setMetricValue(anyLong(), eq("like_count"), eq(10));
    }

    @Test
    @DisplayName("batchSyncLikeCounts - contentIds为空")
    void testBatchSyncLikeCounts_EmptyContentIds() {
        // When
        contentLikeService.batchSyncLikeCounts(Arrays.asList());

        // Then
        verify(contentLikeMapper, never()).countByContentAndType(anyLong(), anyInt());
    }

    @Test
    @DisplayName("batchSyncLikeCounts - contentIds为null")
    void testBatchSyncLikeCounts_NullContentIds() {
        // When
        contentLikeService.batchSyncLikeCounts(null);

        // Then
        verify(contentLikeMapper, never()).countByContentAndType(anyLong(), anyInt());
    }

    // ========================================
    // getTopLikedContents 测试
    // ========================================

    @Test
    @DisplayName("getTopLikedContents - 成功获取热门内容")
    void testGetTopLikedContents_Success() {
        // Given
        List<Long> topContentIds = Arrays.asList(1L, 2L, 3L);
        when(metricMapper.findTopContentsByMetric("like_count", 10)).thenReturn(topContentIds);

        // When
        List<Long> result = contentLikeService.getTopLikedContents(10);

        // Then
        assertNotNull(result);
        assertEquals(3, result.size());
        verify(metricMapper, times(1)).findTopContentsByMetric("like_count", 10);
    }

    // ========================================
    // 辅助方法
    // ========================================

    /**
     * 创建测试用关系对象
     */
    private UserContentRelation createRelation(Long id, Long userId, Long contentId) {
        UserContentRelation relation = new UserContentRelation(userId, contentId, likeRelationType);
        relation.setCreatedDate(LocalDateTime.now());
        return relation;
    }
}