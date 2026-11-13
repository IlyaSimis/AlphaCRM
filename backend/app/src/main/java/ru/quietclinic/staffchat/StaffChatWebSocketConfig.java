package ru.quietclinic.staffchat;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/** Тот же origin и та же HTTP-сессия сотрудника, что у REST API; anonymous handshake запрещён. */
@Configuration
@EnableWebSocket
class StaffChatWebSocketConfig implements WebSocketConfigurer {
  private final StaffChatHub hub;

  StaffChatWebSocketConfig(StaffChatHub hub) {
    this.hub = hub;
  }

  @Override
  public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
    registry.addHandler(hub, "/api/v1/staff-chat/events");
  }
}
