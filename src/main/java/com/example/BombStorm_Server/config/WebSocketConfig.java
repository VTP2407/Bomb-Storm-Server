package com.example.BombStorm_Server.config;

import com.example.BombStorm_Server.WebSocket.GameWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket // Kích hoạt tính năng WebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final GameWebSocketHandler gameWebSocketHandler;

    public WebSocketConfig(GameWebSocketHandler gameWebSocketHandler) {
        this.gameWebSocketHandler = gameWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // Mở endpoint ở URL /game
        // setAllowedOrigins("*") cho phép Unity truy cập mà không bị chặn lỗi CORS
        registry.addHandler(gameWebSocketHandler, "/game").setAllowedOrigins("*");
    }
}
