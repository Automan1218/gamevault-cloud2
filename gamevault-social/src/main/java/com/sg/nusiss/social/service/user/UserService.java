package com.sg.nusiss.social.service.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sg.nusiss.common.dto.UserDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final RestTemplate restTemplate;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String AUTH_SERVICE_URL = "http://gamevault-auth";
    private static final String CACHE_PREFIX = "user:";
    private static final long CACHE_EXPIRE_HOURS = 1;

    /**
     * 根据用户ID查询用户信息（带缓存）
     */
    public UserDTO getUserById(Long userId) {
        if (userId == null) {
            return null;
        }

        // 1. 先查 Redis 缓存
        String cacheKey = CACHE_PREFIX + userId;
        Object cached = redisTemplate.opsForValue().get(cacheKey);

        if (cached != null) {
            log.debug("从缓存获取用户信息: userId={}", userId);

            // 处理 LinkedHashMap 的情况
            try {
                if (cached instanceof UserDTO) {
                    return (UserDTO) cached;
                } else if (cached instanceof Map) {
                    // 使用 ObjectMapper 转换 Map 为 UserDTO
                    return objectMapper.convertValue(cached, UserDTO.class);
                }
            } catch (Exception e) {
                log.warn("缓存数据转换失败，将重新从 Auth 服务获取: userId={}", userId, e);
                // 删除损坏的缓存
                redisTemplate.delete(cacheKey);
            }
        }

        // 2. 调用 Auth 服务
        try {
            String url = AUTH_SERVICE_URL + "/api/users/" + userId;

            ResponseEntity<UserDTO> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    UserDTO.class
            );

            UserDTO user = response.getBody();

            if (user != null) {
                // 3. 存入 Redis 缓存
                redisTemplate.opsForValue().set(cacheKey, user, CACHE_EXPIRE_HOURS, TimeUnit.HOURS);

                log.debug("从 Auth 服务获取用户信息: userId={}", userId);
                return user;
            }
        } catch (Exception e) {
            log.error("调用 Auth 服务失败: userId={}", userId, e);
        }

        // 返回默认值
        return UserDTO.builder()
                .userId(userId)
                .username("未知用户")
                .email("")
                .build();
    }

    /**
     * 批量查询用户信息
     */
    public List<UserDTO> getUsersByIds(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return new ArrayList<>();
        }

        try {
            String url = AUTH_SERVICE_URL + "/api/users/batch";

            ResponseEntity<List<UserDTO>> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    new HttpEntity<>(userIds),
                    new ParameterizedTypeReference<List<UserDTO>>() {}
            );

            List<UserDTO> users = response.getBody();
            return users != null ? users : new ArrayList<>();

        } catch (Exception e) {
            log.error("批量查询用户失败: userIds={}", userIds, e);
        }

        return new ArrayList<>();
    }

    /**
     * 搜索用户
     */
    public List<UserDTO> searchUsers(String keyword) {
        try {
            String url = AUTH_SERVICE_URL + "/api/users/search?keyword=" + keyword;

            ResponseEntity<List<UserDTO>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<List<UserDTO>>() {}
            );

            List<UserDTO> users = response.getBody();
            return users != null ? users : new ArrayList<>();

        } catch (Exception e) {
            log.error("搜索用户失败: keyword={}", keyword, e);
        }

        return new ArrayList<>();
    }
}