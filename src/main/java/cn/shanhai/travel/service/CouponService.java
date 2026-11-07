package cn.shanhai.travel.service;

import cn.shanhai.travel.dto.Api;
import cn.shanhai.travel.repository.CouponRepository;
import java.math.RoundingMode;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 优惠码的管理端规则。
 *
 * <p>这里只管"配置本身合不合法"：码唯一、日期窗口不倒挂、次数上限不能被改到已用次数以下。
 * 至于"这张码能不能用在这笔订单上"（有效期、门槛、剩余次数、启用状态）属于核销校验，见 T6.4；
 * 减免金额的计算在下单流程里按订单原价算，不在本类。
 */
@Service
public class CouponService {
  private final CouponRepository coupons;

  public CouponService(CouponRepository coupons) {
    this.coupons = coupons;
  }

  public List<Api.Coupon> list(String q, Boolean active) {
    return coupons.list(q, active);
  }

  public Api.Coupon get(long id) {
    return coupons.find(id).orElseThrow(() -> ApiException.missing("优惠码不存在"));
  }

  @Transactional
  public Api.Coupon create(Api.CouponInput d) {
    var input = normalize(d);
    requireWindow(input);
    if (coupons.byCode(input.code()).isPresent()) throw ApiException.conflict("此优惠码已存在");
    return coupons.find(coupons.create(input)).orElseThrow();
  }

  @Transactional
  public Api.Coupon update(long id, Api.CouponInput d) {
    var old = coupons.lock(id).orElseThrow(() -> ApiException.missing("优惠码不存在"));
    var input = normalize(d);
    requireWindow(input);
    // 上限压到已用次数以下会让 ck_coupon_uses 直接拒绝执行，先在服务层给出可读原因。
    if (input.maxUses() < old.usedCount())
      throw ApiException.conflict("使用次数上限不能小于已使用次数（" + old.usedCount() + "）");
    var same = coupons.byCode(input.code());
    if (same.isPresent() && same.get().id() != id) throw ApiException.conflict("此优惠码已存在");
    coupons.update(id, input);
    return coupons.find(id).orElseThrow();
  }

  @Transactional
  public void delete(long id) {
    var c = coupons.lock(id).orElseThrow(() -> ApiException.missing("优惠码不存在"));
    // 用过的码不删：订单快照里的折扣需要能对上来源，管理端应改为停用而不是抹掉记录。
    if (c.usedCount() > 0) throw ApiException.conflict("该优惠码已被使用，请改为停用");
    coupons.delete(id);
  }

  /** 码统一大写存储，前端输小写也能命中；金额定到分，避免 0.8 存进去读出来变成 0.8 与 0.80 两种形态。 */
  private static Api.CouponInput normalize(Api.CouponInput d) {
    return new Api.CouponInput(
        d.code().trim().toUpperCase(Locale.ROOT),
        d.description() == null ? "" : d.description().trim(),
        d.discountRate().setScale(2, RoundingMode.HALF_UP),
        d.minAmount().setScale(2, RoundingMode.HALF_UP),
        d.maxUses(),
        d.startsOn(),
        d.endsOn(),
        d.active());
  }

  private static void requireWindow(Api.CouponInput d) {
    if (d.endsOn().isBefore(d.startsOn())) throw ApiException.invalid("结束日期不能早于开始日期");
  }
}
