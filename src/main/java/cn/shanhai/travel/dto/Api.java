package cn.shanhai.travel.dto;

import cn.shanhai.travel.model.Role;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Explicit API models prevent accidental serialization of password hashes or internal fields. */
public final class Api {
  private Api() {}

  public record User(
      long id, String username, String displayName, String phone, Role role, boolean enabled) {}

  public record Login(@NotBlank String username, @NotBlank String password) {}

  public record Register(
      @NotBlank @Pattern(regexp = "[a-zA-Z0-9_]{3,40}", message = "用户名须为 3–40 位字母、数字或下划线")
          String username,
      @NotBlank @Size(max = 60) String displayName,
      @NotBlank @Size(min = 8, max = 72, message = "密码长度须为 8–72 位") String password,
      @NotBlank @Pattern(regexp = "[+0-9 ()-]{5,25}", message = "请输入有效联系电话") String phone) {}

  public record CreateUser(
      @NotBlank @Pattern(regexp = "[a-zA-Z0-9_]{3,40}") String username,
      @NotBlank @Size(max = 60) String displayName,
      @NotBlank @Size(min = 8, max = 72) String password,
      @NotBlank @Pattern(regexp = "[+0-9 ()-]{5,25}") String phone,
      @NotNull Role role) {}

  public record Profile(
      @NotBlank @Size(max = 60) String displayName,
      @NotBlank @Pattern(regexp = "[+0-9 ()-]{5,25}") String phone) {}

  public record UpdateUser(
      @NotBlank @Size(max = 60) String displayName,
      @NotBlank @Pattern(regexp = "[+0-9 ()-]{5,25}") String phone,
      @NotNull Boolean enabled) {}

  public record Password(
      @NotBlank String currentPassword, @NotBlank @Size(min = 8, max = 72) String newPassword) {}

  public record Route(
      long id,
      String name,
      String destination,
      String description,
      LocalDate startDate,
      int durationDays,
      BigDecimal price,
      int capacity,
      int availableSeats,
      boolean active,
      String imageKey,
      List<String> highlights,
      /** 上传图片的存盘文件名；从未上传时为 null，前端回退到 imageKey（C7）。 */
      String imagePath) {}

  public record RouteInput(
      @NotBlank @Size(max = 100) String name,
      @NotBlank @Size(max = 60) String destination,
      @NotBlank @Size(max = 2000) String description,
      @NotNull LocalDate startDate,
      @Min(1) @Max(90) int durationDays,
      @NotNull @DecimalMin("0.01") @DecimalMax("9999999.99") @Digits(integer = 7, fraction = 2)
          BigDecimal price,
      @Min(1) @Max(10000) int capacity,
      @NotNull Boolean active,
      @NotBlank @Pattern(regexp = "guilin|yunnan|xinjiang|chengdu|xiamen|hangzhou") String imageKey,
      @NotNull @Size(max = 6) List<@NotBlank @Size(max = 60) String> highlights) {}

  /**
   * 下单录入。{@code couponCode} 可空：不填表示原价下单，旧前端不必改请求体（C4）。
   *
   * <p>减免金额不接受外部传入——它由服务端按优惠码配置算出来（C12）。
   */
  public record BookingInput(
      @Positive long routeId,
      @Min(1) @Max(20) int travelerCount,
      @NotBlank @Size(max = 60) String contactName,
      @NotBlank @Pattern(regexp = "[+0-9 ()-]{5,25}") String contactPhone,
      @Size(max = 500) String remark,
      @Size(max = 32) String couponCode) {}

  /**
   * 订单。{@code couponCode} / {@code discountAmount} 是下单当时的快照：优惠码配置之后被改写或停用，
   * 历史订单的减免也不会变。
   *
   * <p>两个字段都是新增的（C4）：没用过优惠码的旧订单分别读作 null 与 0，前端按"零减免"展示即可。
   */
  public record Order(
      long id,
      String orderNo,
      long customerId,
      String customerName,
      String customerPhone,
      Long employeeId,
      String employeeName,
      long routeId,
      String routeName,
      String destination,
      LocalDate startDate,
      int durationDays,
      int travelerCount,
      BigDecimal unitPrice,
      BigDecimal totalAmount,
      String status,
      LocalDateTime createdAt,
      String contactName,
      String contactPhone,
      String remark,
      String couponCode,
      BigDecimal discountAmount) {}

  /** 订单状态变更留痕：{@code fromStatus} 为空表示下单建档，此前没有状态。 */
  public record OrderEvent(
      long id,
      long bookingId,
      String fromStatus,
      String toStatus,
      long actorId,
      String actorName,
      String actorRole,
      String note,
      LocalDateTime createdAt) {}

  /** 分页信封：{@code total} 是同一过滤条件下的全量行数，{@code page} 从 0 开始，{@code size} 是真正生效的页大小。 */
  public record Page<T>(List<T> items, long total, int page, int size) {}

  public record PopularRoute(
      long routeId, String name, String destination, long orderCount, int travelerCount) {}

  public record MonthCount(String month, long count) {}

  /** 月度订单量与营收：{@code revenue} 不含已取消订单，缺月由服务层补 0。 */
  public record MonthlyReport(String month, long orders, BigDecimal revenue) {}

  /** 员工接单排行：{@code amount} 为该员工经手订单的合计，不含已取消。 */
  public record EmployeeRank(
      long employeeId, String employeeName, long orderCount, BigDecimal amount) {}

  /**
   * 报表汇总。
   *
   * <p>{@code scope} 说明这份数字代表谁的口径（ADMIN 全量 / CUSTOMER 自己的 / EMPLOYEE 自己经手的），
   * 页面据此写标题，避免把员工个人数字当成全团队。
   */
  public record ReportSummary(
      String scope,
      long totalOrders,
      long pendingOrders,
      long assignedOrders,
      long completedOrders,
      long cancelledOrders,
      BigDecimal totalAmount,
      BigDecimal completedAmount,
      List<MonthlyReport> monthly,
      List<PopularRoute> popularRoutes,
      List<EmployeeRank> employeeRank) {}

  /**
   * 优惠码配置。
   *
   * <p>{@code discountRate} 是折扣率（{@code 0.80} 表示八折），{@code usedCount} / {@code maxUses} 是核销计数，
   * 校验与减免金额都不在这里计算——配置只描述规则，算钱发生在下单流程。
   */
  public record Coupon(
      long id,
      String code,
      String description,
      BigDecimal discountRate,
      BigDecimal minAmount,
      int maxUses,
      int usedCount,
      LocalDate startsOn,
      LocalDate endsOn,
      boolean active,
      LocalDateTime createdAt) {}

  /** 优惠码录入：只配规则，减免金额由下单时按订单原价算出，不接受外部传入。 */
  public record CouponInput(
      @NotBlank
          @Pattern(
              regexp = "[a-zA-Z0-9_-]{3,32}",
              message = "优惠码须为 3–32 位字母、数字、下划线或连字符")
          String code,
      @Size(max = 200) String description,
      @NotNull @DecimalMin("0.01") @DecimalMax("0.99") @Digits(integer = 1, fraction = 2)
          BigDecimal discountRate,
      @NotNull @DecimalMin("0.00") @DecimalMax("9999999.99") @Digits(integer = 7, fraction = 2)
          BigDecimal minAmount,
      @Min(1) @Max(100000) int maxUses,
      @NotNull LocalDate startsOn,
      @NotNull LocalDate endsOn,
      @NotNull Boolean active) {}

  /**
   * 优惠码试算结果：{@code discountAmount} 是减免额，{@code payableAmount} 是折后应收。
   *
   * <p>两个金额都由服务端按配置算出，不接收外部传入（C12）；精度固定两位小数。
   */
  public record CouponQuote(
      String code,
      BigDecimal originalAmount,
      BigDecimal discountAmount,
      BigDecimal payableAmount) {}

  public record Dashboard(
      long totalOrders,
      long pendingOrders,
      long assignedOrders,
      long completedOrders,
      long cancelledOrders,
      BigDecimal totalAmount,
      BigDecimal completedAmount,
      long activeRoutes,
      long customerCount,
      long employeeCount,
      List<Order> recentOrders,
      List<PopularRoute> popularRoutes,
      List<MonthCount> monthlyOrders) {}
}
