package cn.shanhai.travel.controller;

import cn.shanhai.travel.dto.Api;
import cn.shanhai.travel.service.*;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** 优惠码管理端：配置的增删改查只对管理员开放，客户不使用这个入口（在下单时填码）。 */
@RestController
@RequestMapping("/api")
public class CouponController {
  private final CouponService coupons;

  public CouponController(CouponService coupons) {
    this.coupons = coupons;
  }

  @GetMapping("/coupons")
  @PreAuthorize("hasRole('ADMIN')")
  public List<Api.Coupon> coupons(
      @RequestParam(required = false) String q, @RequestParam(required = false) Boolean active) {
    return coupons.list(q, active);
  }

  @GetMapping("/coupons/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  public Api.Coupon coupon(@PathVariable long id) {
    return coupons.get(id);
  }

  @PostMapping("/coupons")
  @PreAuthorize("hasRole('ADMIN')")
  public Api.Coupon createCoupon(@Valid @RequestBody Api.CouponInput d) {
    return coupons.create(d);
  }

  @PutMapping("/coupons/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  public Api.Coupon updateCoupon(@PathVariable long id, @Valid @RequestBody Api.CouponInput d) {
    return coupons.update(id, d);
  }

  @DeleteMapping("/coupons/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  public Map<String, String> deleteCoupon(@PathVariable long id) {
    coupons.delete(id);
    return Map.of("message", "优惠码已删除");
  }
}
