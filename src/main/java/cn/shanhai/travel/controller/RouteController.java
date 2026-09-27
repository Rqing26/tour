package cn.shanhai.travel.controller;

import cn.shanhai.travel.dto.Api;
import cn.shanhai.travel.service.*;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class RouteController {
  private final RouteService routes;

  public RouteController(RouteService routes) {
    this.routes = routes;
  }

  @GetMapping("/routes")
  public List<Api.Route> routes(
      @RequestParam(required = false) String q,
      @RequestParam(required = false) String destination,
      @RequestParam(required = false) Boolean active) {
    return routes.list(q, destination, active);
  }

  @GetMapping("/routes/{id}")
  public Api.Route route(@PathVariable long id) {
    return routes.get(id);
  }

  @PostMapping("/routes")
  @PreAuthorize("hasRole('ADMIN')")
  public Api.Route createRoute(@Valid @RequestBody Api.RouteInput d) {
    return routes.create(d);
  }

  @PutMapping("/routes/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  public Api.Route updateRoute(@PathVariable long id, @Valid @RequestBody Api.RouteInput d) {
    return routes.update(id, d);
  }

  @DeleteMapping("/routes/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  public Map<String, String> deleteRoute(@PathVariable long id) {
    routes.delete(id);
    return message("线路已删除");
  }

  private Map<String, String> message(String value) {
    return Map.of("message", value);
  }
}
