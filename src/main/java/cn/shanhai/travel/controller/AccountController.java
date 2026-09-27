package cn.shanhai.travel.controller;

import cn.shanhai.travel.dto.Api;
import cn.shanhai.travel.service.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class AccountController {
  private final UserService users;

  public AccountController(UserService users) {
    this.users = users;
  }

  @PutMapping("/account")
  public Api.User profile(@Valid @RequestBody Api.Profile d) {
    return users.profile(d);
  }

  @PutMapping("/account/password")
  public Map<String, String> password(
      @Valid @RequestBody Api.Password d, HttpServletRequest request) {
    users.password(d);
    var session = request.getSession(false);
    if (session != null) session.invalidate();
    SecurityContextHolder.clearContext();
    return message("密码已更新，请重新登录");
  }

  private Map<String, String> message(String value) {
    return Map.of("message", value);
  }
}
