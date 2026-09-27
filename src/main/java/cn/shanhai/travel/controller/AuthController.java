package cn.shanhai.travel.controller;

import cn.shanhai.travel.dto.Api;
import cn.shanhai.travel.repository.UserRepository;
import cn.shanhai.travel.service.UserService;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
  private final AuthenticationManager manager;
  private final SecurityContextRepository contexts;
  private final CsrfTokenRepository csrf;
  private final UserService users;
  private final UserRepository accounts;
  private final Environment environment;

  public AuthController(
      AuthenticationManager manager,
      SecurityContextRepository contexts,
      CsrfTokenRepository csrf,
      UserService users,
      UserRepository accounts,
      Environment environment) {
    this.manager = manager;
    this.contexts = contexts;
    this.csrf = csrf;
    this.users = users;
    this.accounts = accounts;
    this.environment = environment;
  }

  @GetMapping("/csrf")
  public Map<String, String> csrf(CsrfToken token) {
    return Map.of("token", token.getToken());
  }

  @GetMapping("/config")
  public Map<String, Boolean> config() {
    return Map.of("demoMode", environment.matchesProfiles("demo"));
  }

  @GetMapping("/me")
  public Api.User me() {
    return users.current().view();
  }

  @PostMapping("/register")
  public Api.User register(@Valid @RequestBody Api.Register d) {
    return users.register(d);
  }

  @PostMapping("/login")
  public Api.User login(
      @Valid @RequestBody Api.Login d, HttpServletRequest req, HttpServletResponse res) {
    var auth =
        manager.authenticate(
            UsernamePasswordAuthenticationToken.unauthenticated(d.username(), d.password()));
    new ChangeSessionIdAuthenticationStrategy().onAuthentication(auth, req, res);
    // Rotate CSRF after identity changes; clients fetch /csrf again after login.
    csrf.saveToken(null, req, res);
    var context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(auth);
    SecurityContextHolder.setContext(context);
    contexts.saveContext(context, req, res);
    return accounts.byUsername(auth.getName()).orElseThrow().view();
  }
}
