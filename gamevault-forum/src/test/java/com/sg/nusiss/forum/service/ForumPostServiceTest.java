package com.sg.nusiss.forum.service;

import com.sg.nusiss.common.dto.UserDTO;
import com.sg.nusiss.forum.entity.ForumContent;
import com.sg.nusiss.forum.repository.ForumContentMapper;
import com.sg.nusiss.forum.repository.ForumMetricMapper;
import com.sg.nusiss.forum.service.forum.ForumContentLikeService;
import com.sg.nusiss.forum.service.forum.ForumPostService;
import com.sg.nusiss.forum.service.user.UserService;
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
 * ForumPostService 单元测试
 *
 * 测试要点:
 * 1. 帖子CRUD操作
 * 2. 回复功能
 * 3. 用户信息填充
 * 4. 点赞状态处理
 * 5. 参数验证
 * 6. 权限验证
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ForumPostService 单元测试")
class ForumPostServiceTest {

    @Mock
    private ForumContentMapper contentMapper;

    @Mock
    private ForumMetricMapper metricMapper;

    @Mock
    private ForumContentLikeService contentLikeService;

    @Mock
    private UserService userService;

    @InjectMocks
    private ForumPostService forumPostService;

    private UserDTO testUser;
    private ForumContent testPost;

    @BeforeEach
    void setUp() {
        // 初始化测试用户
        testUser = createTestUser(1L, "testuser", "test@example.com");

        // 初始化测试帖子
        testPost = createTestPost(1L, "Test Title", "Test Body", 1L);
    }

    // ========================================
    // createPost 测试
    // ========================================

    @Test
    @DisplayName("createPost - 成功创建帖子")
    void testCreatePost_Success() {
        // Given
        String title = "Test Post Title";
        String body = "Test Post Body";
        Long authorId = 1L;

        when(contentMapper.insert(any(ForumContent.class))).thenAnswer(invocation -> {
            ForumContent post = invocation.getArgument(0);
            post.setContentId(100L);
            return 1;
        });

        when(userService.getUserById(authorId)).thenReturn(testUser);

        // When
        ForumContent result = forumPostService.createPost(title, body, authorId);

        // Then
        assertNotNull(result);
        assertEquals("Test Post Title", result.getTitle());
        assertEquals("Test Post Body", result.getBody());
        assertEquals(authorId, result.getAuthorId());
        assertEquals("testuser", result.getAuthorName());

        verify(contentMapper, times(1)).insert(any(ForumContent.class));
        verify(metricMapper, times(3)).setMetricValue(anyLong(), anyString(), eq(0));
        verify(userService, times(1)).getUserById(authorId);
    }

    @Test
    @DisplayName("createPost - 标题为空")
    void testCreatePost_EmptyTitle() {
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            forumPostService.createPost("", "Test Body", 1L);
        });

        verify(contentMapper, never()).insert(any(ForumContent.class));
    }

    @Test
    @DisplayName("createPost - 标题为null")
    void testCreatePost_NullTitle() {
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            forumPostService.createPost(null, "Test Body", 1L);
        });

        verify(contentMapper, never()).insert(any(ForumContent.class));
    }

    @Test
    @DisplayName("createPost - 内容为空")
    void testCreatePost_EmptyBody() {
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            forumPostService.createPost("Test Title", "", 1L);
        });

        verify(contentMapper, never()).insert(any(ForumContent.class));
    }

    @Test
    @DisplayName("createPost - 作者ID为null")
    void testCreatePost_NullAuthorId() {
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            forumPostService.createPost("Test Title", "Test Body", null);
        });

        verify(contentMapper, never()).insert(any(ForumContent.class));
    }

    @Test
    @DisplayName("createPost - 数据库插入失败")
    void testCreatePost_InsertFailed() {
        // Given
        when(contentMapper.insert(any(ForumContent.class))).thenReturn(0);

        // When & Then
        assertThrows(RuntimeException.class, () -> {
            forumPostService.createPost("Test Title", "Test Body", 1L);
        });
    }

    @Test
    @DisplayName("createPost - 标题包含空格会被trim")
    void testCreatePost_TrimTitle() {
        // Given
        String title = "  Test Title  ";
        String body = "  Test Body  ";
        Long authorId = 1L;

        when(contentMapper.insert(any(ForumContent.class))).thenAnswer(invocation -> {
            ForumContent post = invocation.getArgument(0);
            assertEquals("Test Title", post.getTitle());
            assertEquals("Test Body", post.getBody());
            post.setContentId(100L);
            return 1;
        });

        when(userService.getUserById(authorId)).thenReturn(testUser);

        // When
        ForumContent result = forumPostService.createPost(title, body, authorId);

        // Then
        assertNotNull(result);
        assertEquals("Test Title", result.getTitle());
        assertEquals("Test Body", result.getBody());
    }

    // ========================================
    // getPostById 测试
    // ========================================

    @Test
    @DisplayName("getPostById - 成功获取帖子")
    void testGetPostById_Success() {
        // Given
        Long postId = 1L;
        Long currentUserId = 2L;

        when(contentMapper.findById(postId)).thenReturn(testPost);
        when(userService.getUserById(testPost.getAuthorId())).thenReturn(testUser);
        when(contentLikeService.isLiked(postId, currentUserId)).thenReturn(true);

        // When
        ForumContent result = forumPostService.getPostById(postId, currentUserId);

        // Then
        assertNotNull(result);
        assertEquals(postId, result.getContentId());
        assertEquals("testuser", result.getAuthorName());
        assertTrue(result.getIsLikedByCurrentUser());

        verify(contentMapper, times(1)).findById(postId);
        verify(userService, times(1)).getUserById(testPost.getAuthorId());
        verify(contentLikeService, times(1)).isLiked(postId, currentUserId);
    }

    @Test
    @DisplayName("getPostById - 帖子不存在")
    void testGetPostById_NotFound() {
        // Given
        Long postId = 999L;
        when(contentMapper.findById(postId)).thenReturn(null);

        // When & Then
        assertThrows(RuntimeException.class, () -> {
            forumPostService.getPostById(postId, null);
        });

        verify(contentMapper, times(1)).findById(postId);
        verify(userService, never()).getUserById(anyLong());
    }

    @Test
    @DisplayName("getPostById - 未登录用户不查询点赞状态")
    void testGetPostById_NoCurrentUser() {
        // Given
        Long postId = 1L;
        when(contentMapper.findById(postId)).thenReturn(testPost);
        when(userService.getUserById(testPost.getAuthorId())).thenReturn(testUser);

        // When
        ForumContent result = forumPostService.getPostById(postId, null);

        // Then
        assertNotNull(result);
        verify(contentLikeService, never()).isLiked(anyLong(), anyLong());
    }

    @Test
    @DisplayName("getPostById - 用户信息获取失败时显示未知用户")
    void testGetPostById_UserNotFound() {
        // Given
        Long postId = 1L;
        when(contentMapper.findById(postId)).thenReturn(testPost);
        when(userService.getUserById(testPost.getAuthorId())).thenReturn(null);

        // When
        ForumContent result = forumPostService.getPostById(postId, null);

        // Then
        assertNotNull(result);
        assertEquals("未知用户", result.getAuthorName());
        assertNull(result.getAuthorAvatar());
    }

    // ========================================
    // getPostList 测试
    // ========================================

    @Test
    @DisplayName("getPostList - 成功获取帖子列表")
    void testGetPostList_Success() {
        // Given
        int page = 0;
        int size = 10;
        Long currentUserId = 2L;

        List<ForumContent> posts = Arrays.asList(
                createTestPost(1L, "Post 1", "Body 1", 1L),
                createTestPost(2L, "Post 2", "Body 2", 1L)
        );

        when(contentMapper.findActivePosts(0, 10)).thenReturn(posts);
        when(userService.getUsersByIds(anyList())).thenReturn(Arrays.asList(testUser));

        Map<Long, Boolean> likeStatus = new HashMap<>();
        likeStatus.put(1L, true);
        likeStatus.put(2L, false);
        when(contentLikeService.batchCheckLikeStatus(eq(currentUserId), anyList())).thenReturn(likeStatus);

        // When
        List<ForumContent> result = forumPostService.getPostList(page, size, currentUserId);

        // Then
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("testuser", result.get(0).getAuthorName());
        assertTrue(result.get(0).getIsLikedByCurrentUser());
        assertFalse(result.get(1).getIsLikedByCurrentUser());

        verify(contentMapper, times(1)).findActivePosts(0, 10);
        verify(userService, times(1)).getUsersByIds(anyList());
    }

    @Test
    @DisplayName("getPostList - 空列表")
    void testGetPostList_EmptyList() {
        // Given
        when(contentMapper.findActivePosts(anyInt(), anyInt())).thenReturn(Arrays.asList());

        // When
        List<ForumContent> result = forumPostService.getPostList(0, 10, null);

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(userService, never()).getUsersByIds(anyList());
    }

    @Test
    @DisplayName("getPostList - 未登录用户不查询点赞状态")
    void testGetPostList_NoCurrentUser() {
        // Given
        List<ForumContent> posts = Arrays.asList(createTestPost(1L, "Post 1", "Body 1", 1L));
        when(contentMapper.findActivePosts(anyInt(), anyInt())).thenReturn(posts);
        when(userService.getUsersByIds(anyList())).thenReturn(Arrays.asList(testUser));

        // When
        List<ForumContent> result = forumPostService.getPostList(0, 10, null);

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        verify(contentLikeService, never()).batchCheckLikeStatus(anyLong(), anyList());
    }

    // ========================================
    // deletePost 测试
    // ========================================

    @Test
    @DisplayName("deletePost - 成功删除帖子")
    void testDeletePost_Success() {
        // Given
        Long postId = 1L;
        Long userId = 1L;

        when(contentMapper.findById(postId)).thenReturn(testPost);

        // When
        forumPostService.deletePost(postId, userId);

        // Then
        verify(contentMapper, times(1)).findById(postId);
        verify(contentMapper, times(1)).softDelete(postId);
    }

    @Test
    @DisplayName("deletePost - 帖子ID为null")
    void testDeletePost_NullPostId() {
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            forumPostService.deletePost(null, 1L);
        });

        verify(contentMapper, never()).softDelete(anyLong());
    }

    @Test
    @DisplayName("deletePost - 帖子不存在")
    void testDeletePost_PostNotFound() {
        // Given
        Long postId = 999L;
        when(contentMapper.findById(postId)).thenReturn(null);

        // When & Then
        assertThrows(RuntimeException.class, () -> {
            forumPostService.deletePost(postId, 1L);
        });

        verify(contentMapper, never()).softDelete(anyLong());
    }

    @Test
    @DisplayName("deletePost - 无权限删除")
    void testDeletePost_NoPermission() {
        // Given
        Long postId = 1L;
        Long userId = 999L; // 不是作者

        when(contentMapper.findById(postId)).thenReturn(testPost);

        // When & Then
        assertThrows(RuntimeException.class, () -> {
            forumPostService.deletePost(postId, userId);
        });

        verify(contentMapper, never()).softDelete(anyLong());
    }

    // ========================================
    // updatePost 测试
    // ========================================

    @Test
    @DisplayName("updatePost - 成功更新帖子")
    void testUpdatePost_Success() {
        // Given
        Long postId = 1L;
        String newTitle = "Updated Title";
        String newBody = "Updated Body";
        Long userId = 1L;

        when(contentMapper.findById(postId)).thenReturn(testPost);
        when(contentMapper.update(any(ForumContent.class))).thenReturn(1);
        when(userService.getUserById(userId)).thenReturn(testUser);

        // When
        ForumContent result = forumPostService.updatePost(postId, newTitle, newBody, userId);

        // Then
        assertNotNull(result);
        assertEquals("Updated Title", result.getTitle());
        assertEquals("Updated Body", result.getBody());

        verify(contentMapper, times(1)).update(any(ForumContent.class));
        verify(userService, times(1)).getUserById(userId);
    }

    @Test
    @DisplayName("updatePost - 帖子ID为null")
    void testUpdatePost_NullPostId() {
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            forumPostService.updatePost(null, "Title", "Body", 1L);
        });

        verify(contentMapper, never()).update(any(ForumContent.class));
    }

    @Test
    @DisplayName("updatePost - 无权限更新")
    void testUpdatePost_NoPermission() {
        // Given
        Long postId = 1L;
        Long userId = 999L; // 不是作者

        when(contentMapper.findById(postId)).thenReturn(testPost);

        // When & Then
        assertThrows(RuntimeException.class, () -> {
            forumPostService.updatePost(postId, "New Title", "New Body", userId);
        });

        verify(contentMapper, never()).update(any(ForumContent.class));
    }

    @Test
    @DisplayName("updatePost - 只更新标题")
    void testUpdatePost_OnlyTitle() {
        // Given
        Long postId = 1L;
        String newTitle = "New Title";
        Long userId = 1L;

        when(contentMapper.findById(postId)).thenReturn(testPost);
        when(contentMapper.update(any(ForumContent.class))).thenReturn(1);
        when(userService.getUserById(userId)).thenReturn(testUser);

        // When
        ForumContent result = forumPostService.updatePost(postId, newTitle, null, userId);

        // Then
        assertNotNull(result);
        assertEquals("New Title", result.getTitle());
        assertEquals("Test Body", result.getBody()); // 原内容不变
    }

    // ========================================
    // createReply 测试
    // ========================================

    @Test
    @DisplayName("createReply - 成功创建直接回复")
    void testCreateReply_DirectReply_Success() {
        // Given
        Long parentId = 1L;
        String body = "This is a reply";
        Long authorId = 2L;

        when(contentMapper.findById(parentId)).thenReturn(testPost);
        when(contentMapper.insert(any(ForumContent.class))).thenAnswer(invocation -> {
            ForumContent reply = invocation.getArgument(0);
            reply.setContentId(100L);
            return 1;
        });
        when(userService.getUserById(authorId)).thenReturn(testUser);

        // When
        ForumContent result = forumPostService.createReply(parentId, body, authorId, null);

        // Then
        assertNotNull(result);
        assertEquals("This is a reply", result.getBody());
        assertEquals(parentId, result.getParentId());
        assertNull(result.getReplyTo());

        verify(contentMapper, times(1)).insert(any(ForumContent.class));
        verify(metricMapper, times(1)).incrementMetric(parentId, "reply_count", 1);
    }

    @Test
    @DisplayName("createReply - 成功创建楼中楼回复")
    void testCreateReply_NestedReply_Success() {
        // Given
        Long parentId = 1L;
        Long replyToId = 50L;
        String body = "This is a nested reply";
        Long authorId = 2L;

        ForumContent targetReply = createTestReply(replyToId, "Target reply", 3L, parentId);

        when(contentMapper.findById(parentId)).thenReturn(testPost);
        when(contentMapper.findById(replyToId)).thenReturn(targetReply);
        when(contentMapper.insert(any(ForumContent.class))).thenAnswer(invocation -> {
            ForumContent reply = invocation.getArgument(0);
            reply.setContentId(100L);
            return 1;
        });
        when(userService.getUserById(authorId)).thenReturn(testUser);

        // When
        ForumContent result = forumPostService.createReply(parentId, body, authorId, replyToId);

        // Then
        assertNotNull(result);
        assertEquals("This is a nested reply", result.getBody());
        assertEquals(parentId, result.getParentId());
        assertEquals(replyToId, result.getReplyTo());

        verify(contentMapper, times(1)).insert(any(ForumContent.class));
        verify(metricMapper, times(1)).incrementMetric(parentId, "reply_count", 1);
    }

    @Test
    @DisplayName("createReply - 父内容ID为null")
    void testCreateReply_NullParentId() {
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            forumPostService.createReply(null, "Reply body", 1L, null);
        });

        verify(contentMapper, never()).insert(any(ForumContent.class));
    }

    @Test
    @DisplayName("createReply - 回复内容为空")
    void testCreateReply_EmptyBody() {
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            forumPostService.createReply(1L, "", 1L, null);
        });

        verify(contentMapper, never()).insert(any(ForumContent.class));
    }

    @Test
    @DisplayName("createReply - 父内容不存在")
    void testCreateReply_ParentNotFound() {
        // Given
        when(contentMapper.findById(999L)).thenReturn(null);

        // When & Then
        assertThrows(RuntimeException.class, () -> {
            forumPostService.createReply(999L, "Reply body", 1L, null);
        });

        verify(contentMapper, never()).insert(any(ForumContent.class));
    }

    @Test
    @DisplayName("createReply - 目标回复不存在")
    void testCreateReply_ReplyToNotFound() {
        // Given
        Long parentId = 1L;
        Long replyToId = 999L;

        when(contentMapper.findById(parentId)).thenReturn(testPost);
        when(contentMapper.findById(replyToId)).thenReturn(null);

        // When & Then
        assertThrows(RuntimeException.class, () -> {
            forumPostService.createReply(parentId, "Reply body", 1L, replyToId);
        });

        verify(contentMapper, never()).insert(any(ForumContent.class));
    }

    @Test
    @DisplayName("createReply - 目标回复不属于该帖子")
    void testCreateReply_ReplyToDifferentPost() {
        // Given
        Long parentId = 1L;
        Long replyToId = 50L;

        ForumContent targetReply = createTestReply(replyToId, "Target reply", 3L, 999L); // 不同的parentId

        when(contentMapper.findById(parentId)).thenReturn(testPost);
        when(contentMapper.findById(replyToId)).thenReturn(targetReply);

        // When & Then
        assertThrows(RuntimeException.class, () -> {
            forumPostService.createReply(parentId, "Reply body", 1L, replyToId);
        });

        verify(contentMapper, never()).insert(any(ForumContent.class));
    }

    // ========================================
    // getRepliesByPostId 测试
    // ========================================

    @Test
    @DisplayName("getRepliesByPostId - 成功获取回复列表")
    void testGetRepliesByPostId_Success() {
        // Given
        Long postId = 1L;
        int page = 0;
        int size = 10;
        Long currentUserId = 2L;

        List<ForumContent> replies = Arrays.asList(
                createTestReply(10L, "Reply 1", 2L, postId),
                createTestReply(11L, "Reply 2", 3L, postId)
        );

        // 创建匹配的用户
        UserDTO user2 = createTestUser(2L, "user2", "user2@example.com");
        UserDTO user3 = createTestUser(3L, "user3", "user3@example.com");

        when(contentMapper.findChildren(postId, 0, 10)).thenReturn(replies);
        when(userService.getUsersByIds(anyList())).thenReturn(Arrays.asList(user2, user3));

        Map<Long, Boolean> likeStatus = new HashMap<>();
        likeStatus.put(10L, true);
        likeStatus.put(11L, false);
        when(contentLikeService.batchCheckLikeStatus(eq(currentUserId), anyList())).thenReturn(likeStatus);

        // When
        List<ForumContent> result = forumPostService.getRepliesByPostId(postId, page, size, currentUserId);

        // Then
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("user2", result.get(0).getAuthorName());  // 第一个回复的作者
        assertEquals("user3", result.get(1).getAuthorName());  // 第二个回复的作者
        assertTrue(result.get(0).getIsLikedByCurrentUser());
        assertFalse(result.get(1).getIsLikedByCurrentUser());
    }

    @Test
    @DisplayName("getRepliesByPostId - 帖子ID为null")
    void testGetRepliesByPostId_NullPostId() {
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            forumPostService.getRepliesByPostId(null, 0, 10, null);
        });
    }

    @Test
    @DisplayName("getRepliesByPostId - 页码参数自动修正")
    void testGetRepliesByPostId_InvalidPagination() {
        // Given
        Long postId = 1L;
        when(contentMapper.findChildren(eq(postId), eq(0), eq(20))).thenReturn(Arrays.asList());

        // When
        forumPostService.getRepliesByPostId(postId, -1, 150, null); // 负数页码,超大size

        // Then
        verify(contentMapper, times(1)).findChildren(postId, 0, 20); // page=0, size=20(默认)
    }

    // ========================================
    // searchPosts 测试
    // ========================================

    @Test
    @DisplayName("searchPosts - 成功搜索帖子")
    void testSearchPosts_Success() {
        // Given
        String keyword = "test";
        int page = 0;
        int size = 10;

        List<ForumContent> posts = Arrays.asList(
                createTestPost(1L, "Test Post", "Body", 1L)
        );

        when(contentMapper.searchPosts(keyword, 0, 10)).thenReturn(posts);
        when(userService.getUsersByIds(anyList())).thenReturn(Arrays.asList(testUser));

        // When
        List<ForumContent> result = forumPostService.searchPosts(keyword, page, size, null);

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        verify(contentMapper, times(1)).searchPosts(keyword, 0, 10);
    }

    @Test
    @DisplayName("searchPosts - 空关键词返回全部帖子")
    void testSearchPosts_EmptyKeyword() {
        // Given
        when(contentMapper.findActivePosts(0, 10)).thenReturn(Arrays.asList());

        // When
        List<ForumContent> result = forumPostService.searchPosts("", 0, 10, null);

        // Then
        assertNotNull(result);
        verify(contentMapper, times(1)).findActivePosts(0, 10);
        verify(contentMapper, never()).searchPosts(anyString(), anyInt(), anyInt());
    }

    // ========================================
    // incrementViewCount 测试
    // ========================================

    @Test
    @DisplayName("incrementViewCount - 成功增加浏览量")
    void testIncrementViewCount_Success() {
        // Given
        Long postId = 1L;
        when(contentMapper.findById(postId)).thenReturn(testPost);

        // When
        forumPostService.incrementViewCount(postId);

        // Then
        verify(contentMapper, times(1)).findById(postId);
        verify(metricMapper, times(1)).incrementMetric(postId, "view_count", 1);
    }

    @Test
    @DisplayName("incrementViewCount - 帖子ID为null")
    void testIncrementViewCount_NullPostId() {
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            forumPostService.incrementViewCount(null);
        });

        verify(metricMapper, never()).incrementMetric(anyLong(), anyString(), anyInt());
    }

    @Test
    @DisplayName("incrementViewCount - 帖子不存在")
    void testIncrementViewCount_PostNotFound() {
        // Given
        Long postId = 999L;
        when(contentMapper.findById(postId)).thenReturn(null);

        // When & Then
        assertThrows(RuntimeException.class, () -> {
            forumPostService.incrementViewCount(postId);
        });

        verify(metricMapper, never()).incrementMetric(anyLong(), anyString(), anyInt());
    }

    // ========================================
    // 辅助方法
    // ========================================

    /**
     * 创建测试用户
     */
    private UserDTO createTestUser(Long userId, String username, String email) {
        UserDTO user = new UserDTO();
        user.setUserId(userId);
        user.setUsername(username);
        user.setEmail(email);
        user.setAvatarUrl("http://example.com/avatar/" + userId + ".jpg");
        return user;
    }

    /**
     * 创建测试帖子
     */
    private ForumContent createTestPost(Long postId, String title, String body, Long authorId) {
        ForumContent post = new ForumContent("post", title, body, authorId);
        post.setContentId(postId);
        post.setCreatedDate(LocalDateTime.now());
        post.setStatus("active");
        post.setLikeCount(0);
        post.setViewCount(0);
        post.setReplyCount(0);
        return post;
    }

    /**
     * 创建测试回复
     */
    private ForumContent createTestReply(Long replyId, String body, Long authorId, Long parentId) {
        ForumContent reply = new ForumContent("reply", body, authorId, parentId);
        reply.setContentId(replyId);
        reply.setCreatedDate(LocalDateTime.now());
        reply.setStatus("active");
        reply.setLikeCount(0);
        return reply;
    }
}