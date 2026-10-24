package cn.shanhai.travel.controller;

import cn.shanhai.travel.dto.ChatApi;
import cn.shanhai.travel.service.ChatService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chat")
public class ChatController {
  private final ChatService chats;
  public ChatController(ChatService chats) { this.chats = chats; }
  @GetMapping("/contacts")
  public List<ChatApi.Contact> contacts() { return chats.contacts(); }
  @GetMapping("/conversations")
  public List<ChatApi.Conversation> conversations() { return chats.list(); }
  @PostMapping("/conversations")
  public ChatApi.Conversation start(@Valid @RequestBody ChatApi.Start input) { return chats.start(input); }
  @GetMapping("/conversations/{id}/messages")
  public List<ChatApi.Message> history(@PathVariable long id, @RequestParam(defaultValue = "9223372036854775807") long beforeId) {
    return chats.history(id, beforeId);
  }
  @PostMapping("/conversations/{id}/messages")
  public ChatApi.Message send(@PathVariable long id, @Valid @RequestBody ChatApi.Send input) { return chats.send(id, input); }
  @PostMapping("/conversations/{id}/read")
  public Map<String, Boolean> read(@PathVariable long id, @Valid @RequestBody ChatApi.Read input) {
    chats.read(id, input); return Map.of("ok", true);
  }
}
