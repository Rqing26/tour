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
      List<String> highlights) {}

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

  public record BookingInput(
      @Positive long routeId,
      @Min(1) @Max(20) int travelerCount,
      @NotBlank @Size(max = 60) String contactName,
      @NotBlank @Pattern(regexp = "[+0-9 ()-]{5,25}") String contactPhone,
      @Size(max = 500) String remark) {}

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
      String remark) {}

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

  public record PopularRoute(
      long routeId, String name, String destination, long orderCount, int travelerCount) {}

  public record MonthCount(String month, long count) {}

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
