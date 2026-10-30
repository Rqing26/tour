package cn.shanhai.travel.service;

import cn.shanhai.travel.dto.Api;
import cn.shanhai.travel.model.*;
import cn.shanhai.travel.repository.*;
import java.time.LocalDate;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {
  private final OrderRepository orders;
  private final RouteRepository routes;
  private final UserService users;
  private final BookingEventRepository events;

  public OrderService(
      OrderRepository orders,
      RouteRepository routes,
      UserService users,
      BookingEventRepository events) {
    this.orders = orders;
    this.routes = routes;
    this.users = users;
    this.events = events;
  }

  public List<Api.Order> list(String q, String status) {
    return list(q, status, null);
  }

  public List<Api.Order> list(String q, String status, String sort) {
    var a = users.current();
    requireStatus(status);
    // 可见性、状态与关键词都下推给 SQL：不再把全表捞进内存，分页窗口才能真的作用在数据库上。
    return orders.page(filter(a, q, status), parseSort(sort), 0, Integer.MAX_VALUE);
  }

  /**
   * 分页查询：页码与页大小都由服务端归一，{@code total} 与当前页共用同一套过滤条件。
   *
   * <p>页大小上限 {@value #MAX_PAGE_SIZE}：前端传多大都不会把整表拉进一次响应。
   */
  public Api.Page<Api.Order> page(String q, String status, Integer page, Integer size, String sort) {
    var a = users.current();
    requireStatus(status);
    int actualSize = Math.min(Math.max(size == null ? DEFAULT_PAGE_SIZE : size, 1), MAX_PAGE_SIZE);
    int current = page == null || page < 0 ? 0 : page;
    var filter = filter(a, q, status);
    // 深翻页的偏移量可能超过 int：先在 long 里算，再压到 LIMIT/OFFSET 能接受的上界。
    long offset = (long) current * actualSize;
    var items =
        orders.page(
            filter, parseSort(sort), (int) Math.min(offset, Integer.MAX_VALUE), actualSize);
    return new Api.Page<>(items, orders.count(filter), current, actualSize);
  }

  /** 页大小默认值与上限：上限之外的请求被压到上限，而不是报错。 */
  static final int DEFAULT_PAGE_SIZE = 10;

  static final int MAX_PAGE_SIZE = 100;

  private static final Set<String> STATUSES =
      Set.of("PENDING", "ASSIGNED", "COMPLETED", "CANCELLED");

  private static void requireStatus(String status) {
    if (status != null && !status.isBlank() && !STATUSES.contains(status))
      throw ApiException.invalid("无效订单状态");
  }

  /**
   * 排序参数形如 {@code createdAt} 或 {@code totalAmount,asc}，只接受白名单键。
   *
   * <p>真实列名由仓储映射，任何用户输入都不会被拼进 SQL；拼错的键直接 400，不静默回退。
   */
  private static OrderRepository.Sort parseSort(String sort) {
    if (sort == null || sort.isBlank()) return OrderRepository.Sort.DEFAULT;
    var parts = sort.split(",");
    if (parts.length > 2) throw ApiException.invalid("排序参数格式应为 字段[,asc|desc]");
    var key = parts[0].trim();
    if (!OrderRepository.Sort.supports(key)) throw ApiException.invalid("无效排序字段：" + key);
    if (parts.length == 1) return new OrderRepository.Sort(key, true);
    var direction = parts[1].trim().toLowerCase(Locale.ROOT);
    if (!direction.equals("asc") && !direction.equals("desc"))
      throw ApiException.invalid("无效排序方向，只支持 asc 或 desc");
    return new OrderRepository.Sort(key, direction.equals("desc"));
  }

  /** 三种角色的可见范围，翻译成仓储能拼进 WHERE 的条件；管理员不限范围，故两个归属字段都为空。 */
  private static OrderRepository.Filter filter(Account a, String q, String status) {
    var wanted = status == null || status.isBlank() ? null : status;
    var keyword = q == null || q.isBlank() ? null : q;
    return switch (a.role()) {
      case ADMIN -> new OrderRepository.Filter(null, null, wanted, keyword);
      case CUSTOMER -> new OrderRepository.Filter(a.id(), null, wanted, keyword);
      case EMPLOYEE -> new OrderRepository.Filter(null, a.id(), wanted, keyword);
    };
  }

  private boolean visible(Account a, Api.Order o) {
    return switch (a.role()) {
      case ADMIN -> true;
      case CUSTOMER -> o.customerId() == a.id();
      case EMPLOYEE -> o.status().equals("PENDING") || Objects.equals(o.employeeId(), a.id());
    };
  }

  /** 读取订单留痕。可见性与订单列表同一口径：看不到的订单，其变更历史也不返回。 */
  public List<Api.OrderEvent> timeline(long id) {
    var a = users.current();
    var o = orders.find(id).orElseThrow(() -> ApiException.missing("订单不存在"));
    if (!visible(a, o)) throw ApiException.forbidden();
    return events.byBooking(id);
  }

  @Transactional
  public Api.Order create(Api.BookingInput d) {
    var a = users.lockCurrent();
    if (a.role() != Role.CUSTOMER) throw ApiException.forbidden();
    // Every seat-changing operation locks the route first, preventing concurrent overselling.
    var r = routes.lock(d.routeId()).orElseThrow(() -> ApiException.missing("线路不存在"));
    if (!r.active() || !r.startDate().isAfter(LocalDate.now()))
      throw ApiException.conflict("此线路已下架或已停止预订");
    if (d.travelerCount() > r.availableSeats())
      throw ApiException.conflict("余位不足，当前剩余 " + r.availableSeats() + " 位");
    long id = orders.create(a.id(), r, d);
    // 下单即建档：没有上一状态，这条事件是时间线的起点。
    events.append(id, null, "PENDING", a.id(), a.role().name(), "");
    return orders.find(id).orElseThrow();
  }

  @Transactional
  public Api.Order claim(long id) {
    var a = users.lockCurrent();
    if (a.role() != Role.EMPLOYEE) throw ApiException.forbidden();
    var o = orders.lock(id).orElseThrow(() -> ApiException.missing("订单不存在"));
    if (!o.status().equals("PENDING")) throw ApiException.conflict("订单状态已变化，请刷新后重试");
    transition(a, o, "ASSIGNED", a.id());
    return orders.find(id).orElseThrow();
  }

  @Transactional
  public Api.Order release(long id) {
    return employeeTransition(id, "PENDING");
  }

  @Transactional
  public Api.Order complete(long id) {
    return employeeTransition(id, "COMPLETED");
  }

  private Api.Order employeeTransition(long id, String status) {
    var a = users.lockCurrent();
    var o = orders.lock(id).orElseThrow(() -> ApiException.missing("订单不存在"));
    if (a.role() != Role.EMPLOYEE || !Objects.equals(o.employeeId(), a.id()))
      throw ApiException.forbidden();
    if (!o.status().equals("ASSIGNED")) throw ApiException.conflict("只有已受理订单可以执行此操作");
    transition(a, o, status, status.equals("PENDING") ? null : a.id());
    return orders.find(id).orElseThrow();
  }

  @Transactional
  public Api.Order cancel(long id) {
    var a = users.lockCurrent();
    var initial = orders.find(id).orElseThrow(() -> ApiException.missing("订单不存在"));
    if (a.role() != Role.CUSTOMER || initial.customerId() != a.id()) throw ApiException.forbidden();
    routes.lock(initial.routeId());
    var o = orders.lock(id).orElseThrow(() -> ApiException.missing("订单不存在"));
    if (!Set.of("PENDING", "ASSIGNED").contains(o.status()))
      throw ApiException.conflict("已完成或已取消的订单无法取消");
    transition(a, o, "CANCELLED", o.employeeId());
    return orders.find(id).orElseThrow();
  }

  private void transition(Account actor, Api.Order old, String status, Long employee) {
    if (!orders.compareAndTransition(old, status, employee))
      throw ApiException.conflict("订单已被其他人更新，请刷新后重试");
    // 与状态变更共用同一个 @Transactional 边界：没有新开事务，状态与事件要么一起成功要么一起回滚。
    events.append(old.id(), old.status(), status, actor.id(), actor.role().name(), "");
  }
}
