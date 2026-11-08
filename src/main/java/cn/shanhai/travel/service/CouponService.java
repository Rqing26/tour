package cn.shanhai.travel.service;

import cn.shanhai.travel.dto.Api;
import cn.shanhai.travel.repository.CouponRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 优惠码规则：管理端只管"配置合不合法"，下单侧管"这张码能不能用在这笔订单上、能减多少"。
 *
 * <p>配置校验（T6.2）：码归一后唯一、日期窗口不倒挂、上限不得压到已用次数以下。
 * 核销侧（T6.3–T6.5）：先过有效期/门槛/次数/启用状态四道校验，再用条件自增占掉一次名额，
 * 最后按订单原价算减免。
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

  /**
   * 核销一次并返回试算结果：校验 → 条件自增 → 算钱，三步都在调用方的事务里。
   *
   * <p>方法自身不新开事务：下单流程已经带着"锁线路 + 写订单"的事务边界进来，
   * 这里另起一个会让"次数加了但订单没写进去"变成可能。单独调用时（例如以后做试算接口）
   * 由 {@code REQUIRED} 兜底开一个。
   */
  @Transactional
  public Api.CouponQuote redeem(String code, BigDecimal originalAmount) {
    var coupon = usable(code, originalAmount);
    // 读到的 usedCount 只是提示；真正的闸门是这条条件更新，影响行数 0 说明名额是别人先抢走的。
    if (!coupons.consume(coupon.id())) throw ApiException.conflict("优惠码使用次数已达上限");
    return compute(coupon, originalAmount);
  }

  /**
   * 按订单原价算减免：应收 = 原价 × 折扣率，四舍五入到分；减免 = 原价 − 应收。
   *
   * <p>金额一律 {@code BigDecimal}，不用 double：{@code 0.12} 这类值在二进制里是无限小数，
   * 乘出来的第三位小数会莫名其妙地落在四舍五入的边界上，差一分钱。
   *
   * <p>应收用 {@code min(原价)} 兜底：配置表有 {@code discount_rate < 1} 的约束，正常不会越界，
   * 但人工改库或将来放宽约束时，也不该出现"用码反而涨价"。有了这道兜底，减免天然非负，
   * 不需要再单独夹一次零——多夹一次就是永远不会执行的分支。
   */
  public static Api.CouponQuote compute(Api.Coupon c, BigDecimal originalAmount) {
    var original = originalAmount.setScale(2, RoundingMode.HALF_UP);
    var payable =
        original.multiply(c.discountRate()).setScale(2, RoundingMode.HALF_UP).min(original);
    var discount = original.subtract(payable);
    return new Api.CouponQuote(c.code(), original, discount, payable);
  }

  /** 核销前的四道校验，顺序按"最容易被用户搞错"排：不存在 → 停用 → 未生效/过期 → 门槛 → 次数。 */
  private Api.Coupon usable(String code, BigDecimal originalAmount) {
    var wanted = code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
    if (wanted.isEmpty()) throw ApiException.invalid("请填写优惠码");
    var c = coupons.byCode(wanted).orElseThrow(() -> ApiException.invalid("优惠码不存在"));
    if (!c.active()) throw ApiException.invalid("优惠码已停用，无法使用");
    var today = LocalDate.now();
    if (today.isBefore(c.startsOn())) throw ApiException.invalid("优惠码尚未开始使用");
    if (today.isAfter(c.endsOn())) throw ApiException.invalid("优惠码已过期");
    if (originalAmount.compareTo(c.minAmount()) < 0)
      throw ApiException.invalid(
          "订单金额未满 " + c.minAmount().toPlainString() + " 元，不能使用此优惠码");
    if (c.usedCount() >= c.maxUses()) throw ApiException.conflict("优惠码使用次数已达上限");
    return c;
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
