package cn.shanhai.travel.service;

import cn.shanhai.travel.model.*;
import cn.shanhai.travel.repository.UserRepository;
import jakarta.annotation.PreDestroy;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.*;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Push invalidations after commit; message bodies remain behind normal authenticated APIs. */
@Service
public class ChatEvents {
  @Configuration
  @EnableScheduling
  static class Scheduling {}

  public record Changed(long conversationId, long customerId, long employeeId) {}
  private record Client(Account account, HttpSession session) {}
  private final Map<SseEmitter, Client> clients = new ConcurrentHashMap<>();
  private final UserRepository users;
  public ChatEvents(UserRepository users) { this.users = users; }

  public SseEmitter subscribe(Account account, HttpSession session) {
    var emitter = new SseEmitter(300_000L);
    clients.put(emitter, new Client(account, session));
    emitter.onCompletion(() -> clients.remove(emitter));
    emitter.onTimeout(() -> { clients.remove(emitter); emitter.complete(); });
    emitter.onError(error -> clients.remove(emitter));
    send(emitter, "ready", Map.of("connected", true));
    return emitter;
  }

  private boolean valid(Client client) {
    try {
      var session = client.session();
      if (session.getMaxInactiveInterval() > 0
          && System.currentTimeMillis() - session.getLastAccessedTime() > session.getMaxInactiveInterval() * 1000L) return false;
      var context = session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
      if (!(context instanceof SecurityContext security) || security.getAuthentication() == null
          || !(security.getAuthentication().getPrincipal() instanceof AccountPrincipal principal)
          || principal.id() != client.account().id()) return false;
      return users.byId(client.account().id()).filter(a -> a.enabled()
          && a.passwordHash().equals(client.account().passwordHash())).isPresent();
    } catch (IllegalStateException expired) { return false; }
  }

  private void send(SseEmitter emitter, String name, Object data) {
    try {
      emitter.send(SseEmitter.event().name(name).data(data).reconnectTime(3000));
    } catch (IOException | IllegalStateException closed) {
      clients.remove(emitter);
    }
  }
  @TransactionalEventListener
  public void changed(Changed event) {
    clients.forEach((emitter, client) -> {
      long id = client.account().id();
      if (id != event.customerId() && id != event.employeeId()) return;
      if (!valid(client)) { clients.remove(emitter); emitter.complete(); return; }
      send(emitter, "chat", Map.of("conversationId", event.conversationId()));
    });
  }
  @Scheduled(fixedDelay = 15000)
  public void heartbeat() {
    clients.forEach((emitter, client) -> {
      if (valid(client)) send(emitter, "heartbeat", Map.of("connected", true));
      else { clients.remove(emitter); emitter.complete(); }
    });
  }
  @PreDestroy
  void close() { clients.keySet().forEach(SseEmitter::complete); clients.clear(); }
}
