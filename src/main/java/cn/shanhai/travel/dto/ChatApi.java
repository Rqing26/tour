package cn.shanhai.travel.dto;

import cn.shanhai.travel.model.Role;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;

public final class ChatApi {
  private ChatApi() {}

  public record Contact(long id, String displayName, Role role, boolean enabled) {}
  public record Conversation(long id, Contact peer, String lastMessage,
      LocalDateTime updatedAt, long unreadCount, long peerReadId) {}
  public record Message(long id, long conversationId, long senderId, String content,
      String clientId, LocalDateTime createdAt) {}
  public record Start(@Positive long peerId) {}
  public record Send(@NotBlank @Size(max = 2000) String content,
      @NotBlank @Pattern(regexp = "[a-fA-F0-9]{8}(-[a-fA-F0-9]{4}){3}-[a-fA-F0-9]{12}") String clientId) {}
  public record Read(@Positive long messageId) {}
}
