package cn.shanhai.travel.controller;

import cn.shanhai.travel.dto.ChatApi;
import cn.shanhai.travel.service.ChatService;
import cn.shanhai.travel.service.ChatEvents;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chat")
public class ChatController {
  private final ChatService chats;
  private final ChatEvents events;
  public ChatController(ChatService chats, ChatEvents events) { this.chats = chats; this.events = events; }
  @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public ResponseEntity<SseEmitter> events(HttpSession session) {
    return ResponseEntity.ok().header("X-Accel-Buffering", "no").cacheControl(CacheControl.noStore())
        .body(events.subscribe(chats.actor(), session));
  }
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
