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
    var a = users.current();
    if (status != null
        && !status.isBlank()
        && !Set.of("PENDING", "ASSIGNED", "COMPLETED", "CANCELLED").contains(status))
      throw ApiException.invalid("无效订单状态");
    return orders.all().stream()
        .filter(o -> visible(a, o))
        .filter(o -> status == null || status.isBlank() || o.status().equals(status))
        .filter(
            o ->
                q == null
                    || (o.orderNo()
                            + " "
                            + o.routeName()
                            + " "
                            + o.customerName()
                            + " "
                            + o.contactName()
                            + " "
                            + Objects.toString(o.employeeName(), ""))
                        .toLowerCase(Locale.ROOT)
                        .contains(q.toLowerCase(Locale.ROOT)))
        .toList();
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
