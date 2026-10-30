package cn.shanhai.travel.controller;

import cn.shanhai.travel.dto.Api;
import cn.shanhai.travel.service.*;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class OrderController {
  private final OrderService orders;

  public OrderController(OrderService orders) {
    this.orders = orders;
  }

  /**
   * 订单列表。不带 {@code page}/{@code size} 时保持原数组响应（已有调用方不受影响），
   * 带上任一参数才返回分页信封 {@code {items,total,page,size}}。
   */
  @GetMapping("/orders")
  public Object orders(
      @RequestParam(required = false) String q,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size,
      @RequestParam(required = false) String sort) {
    if (page == null && size == null) return orders.list(q, status, sort);
    return orders.page(q, status, page, size, sort);
  }

  @GetMapping("/orders/{id}/events")
  public List<Api.OrderEvent> events(@PathVariable long id) {
    return orders.timeline(id);
  }

  @PostMapping("/orders")
  @PreAuthorize("hasRole('CUSTOMER')")
  public Api.Order book(@Valid @RequestBody Api.BookingInput d) {
    return orders.create(d);
  }

  @PostMapping("/orders/{id}/claim")
  @PreAuthorize("hasRole('EMPLOYEE')")
  public Api.Order claim(@PathVariable long id) {
    return orders.claim(id);
  }

  @PostMapping("/orders/{id}/release")
  @PreAuthorize("hasRole('EMPLOYEE')")
  public Api.Order release(@PathVariable long id) {
    return orders.release(id);
  }

  @PostMapping("/orders/{id}/complete")
  @PreAuthorize("hasRole('EMPLOYEE')")
  public Api.Order complete(@PathVariable long id) {
    return orders.complete(id);
  }

  @PostMapping("/orders/{id}/cancel")
  @PreAuthorize("hasRole('CUSTOMER')")
  public Api.Order cancel(@PathVariable long id) {
    return orders.cancel(id);
  }
}
