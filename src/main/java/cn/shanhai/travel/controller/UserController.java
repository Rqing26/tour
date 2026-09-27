package cn.shanhai.travel.controller;

import cn.shanhai.travel.dto.Api;
import cn.shanhai.travel.model.Role;
import cn.shanhai.travel.service.*;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class UserController {
  private final UserService users;

  public UserController(UserService users) {
    this.users = users;
  }

  @GetMapping("/users")
  @PreAuthorize("hasRole('ADMIN')")
  public List<Api.User> users(
      @RequestParam(required = false) String q, @RequestParam(required = false) Role role) {
    return users.list(q, role);
  }

  @PostMapping("/users")
  @PreAuthorize("hasRole('ADMIN')")
  public Api.User createUser(@Valid @RequestBody Api.CreateUser d) {
    return users.create(d);
  }

  @PutMapping("/users/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  public Api.User updateUser(@PathVariable long id, @Valid @RequestBody Api.UpdateUser d) {
    return users.update(id, d);
  }

  @DeleteMapping("/users/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  public Map<String, String> deleteUser(@PathVariable long id) {
    users.delete(id);
    return message("用户已删除");
  }

  private Map<String, String> message(String value) {
    return Map.of("message", value);
  }
}
