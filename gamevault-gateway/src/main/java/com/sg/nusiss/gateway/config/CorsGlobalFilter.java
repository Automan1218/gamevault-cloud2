package com.sg.nusiss.gateway.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;

/**
 * Gateway 全局 CORS 过滤器
 * 处理跨域请求，支持预检请求（OPTIONS）
 */
@Configuration
public class CorsGlobalFilter implements WebFilter, Ordered {

    // 定义允许的源列表
    private static final List<String> ALLOWED_ORIGINS = Arrays.asList(
            "http://localhost:3000",
            "http://127.0.0.1:3000",
            "http://localhost:3001",
            "http://127.0.0.1:3001",
            "http://3.0.15.128:3000",
            "http://3.0.15.128:3001",
            "http://3.0.15.128:30130",
            "http://3.0.15.128:30131",
            "http://3.0.15.128:30187",
            "http://3.0.15.128:8080",
            "http://3.0.15.128"
    );

    // 定义允许的请求头
    private static final List<String> ALLOWED_HEADERS = Arrays.asList(
            "Authorization",
            "Content-Type",
            "Accept",
            "Origin",
            "X-Requested-With",
            "Access-Control-Request-Method",
            "Access-Control-Request-Headers"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpResponse response = exchange.getResponse();
        HttpHeaders headers = response.getHeaders();

        // 获取请求的 Origin
        String requestOrigin = exchange.getRequest().getHeaders().getFirst("Origin");

        // 动态设置 Access-Control-Allow-Origin（只设置一次）
        String allowedOrigin = "http://localhost:3000";  // 默认值
        if (requestOrigin != null && ALLOWED_ORIGINS.contains(requestOrigin)) {
            allowedOrigin = requestOrigin;  // 使用请求中的 Origin
        }
        headers.set("Access-Control-Allow-Origin", allowedOrigin);

        // 设置允许的方法
        headers.set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, PATCH, OPTIONS");

        // 设置允许的请求头（明确列出，不用 *）
        headers.set("Access-Control-Allow-Headers", String.join(", ", ALLOWED_HEADERS));

        // 允许携带凭证
        headers.set("Access-Control-Allow-Credentials", "true");

        // 预检请求缓存时间
        headers.set("Access-Control-Max-Age", "3600");

        // 允许前端访问的响应头
        headers.set("Access-Control-Expose-Headers", "Authorization, Content-Type");

        // 处理 OPTIONS 预检请求
        if (exchange.getRequest().getMethod() == HttpMethod.OPTIONS) {
            response.setStatusCode(HttpStatus.OK);
            return response.setComplete();
        }

        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return -100; // 高优先级，确保 CORS 在其他过滤器之前处理
    }
}