package cn.shanhai.travel;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import cn.shanhai.travel.repository.BookingEventRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * T2.6：订单审计的四种状态迁移、同事务回滚与时间线可见性矩阵。
 *
 * <p>与 {@link BookingEventTest} 的分工：那边验证表结构、仓储读写和接口契约，这里只回答两个问题——"每种迁移是否都留下了正确的前后状态与操作人"，以及"状态与事件是否真的同生共死"。
 */
class OrderEventTest extends AbstractApiTest {
  /** 用 spy 包住真实仓储：读仍走真实数据库，只在需要时让写入失败以验证回滚。 */
  @MockitoSpyBean BookingEventRepository events;

  private long placed(Client customer, int seats) throws Exception {
    return createOrder(customer, route(5), seats);
  }

  private List<String> trail(long id) {
    return events.byBooking(id).stream()
        .map(e -> (e.fromStatus() == null ? "(new)" : e.fromStatus()) + "->" + e.toStatus())
        .toList();
  }

  /** 迁移一：PENDING → ASSIGNED。接单是员工第一次介入，操作人必须是接单本人。 */
  @Test
  void claimRecordsPendingToAssigned() throws Exception {
    var customer = login("customer");
    var employee = login("employee");
    long id = placed(customer, 2);

    ok(employee, "POST", "/api/orders/" + id + "/claim", Map.of());

    var order = orders.find(id).orElseThrow();
    assertEquals("ASSIGNED", order.status());
    assertEquals(users.byUsername("employee").orElseThrow().id(), order.employeeId());
    assertEquals(List.of("(new)->PENDING", "PENDING->ASSIGNED"), trail(id));
    var last = events.byBooking(id).get(1);
    assertEquals("EMPLOYEE", last.actorRole());
    assertEquals(order.employeeId(), last.actorId());
  }

  /** 迁移二：ASSIGNED → PENDING。退回意味着这次接单作废，负责人必须清空，否则会被再次"接单"卡住。 */
  @Test
  void releaseReturnsOrderToPendingAndClearsAssignee() throws Exception {
    var customer = login("customer");
    var employee = login("employee");
    long id = placed(customer, 1);
    ok(employee, "POST", "/api/orders/" + id + "/claim", Map.of());

    ok(employee, "POST", "/api/orders/" + id + "/release", Map.of());

    var order = orders.find(id).orElseThrow();
    assertEquals("PENDING", order.status());
    assertNull(order.employeeId());
    assertEquals(List.of("(new)->PENDING", "PENDING->ASSIGNED", "ASSIGNED->PENDING"), trail(id));
    var last = events.byBooking(id).get(2);
    assertEquals("ASSIGNED", last.fromStatus());
    assertEquals("PENDING", last.toStatus());
  }

  /** 迁移三：ASSIGNED → COMPLETED。完成是终态，事件里的操作人仍是完成它的员工。 */
  @Test
  void completeRecordsAssignedToCompleted() throws Exception {
    var customer = login("customer");
    var employee = login("employee");
    long id = placed(customer, 1);
    ok(employee, "POST", "/api/orders/" + id + "/claim", Map.of());

    ok(employee, "POST", "/api/orders/" + id + "/complete", Map.of());

    assertEquals("COMPLETED", orders.find(id).orElseThrow().status());
    assertEquals(
        List.of("(new)->PENDING", "PENDING->ASSIGNED", "ASSIGNED->COMPLETED"), trail(id));
    var last = events.byBooking(id).get(2);
    assertEquals("EMPLOYEE", last.actorRole());
    assertNotNull(last.createdAt());
  }

  /** 迁移四之一：PENDING → CANCELLED。客户取消未接单的订单，操作人是客户本人，订单不留负责人。 */
  @Test
  void cancelFromPendingRecordsCustomerAsActor() throws Exception {
    var customer = login("customer");
    long id = placed(customer, 1);

    ok(customer, "POST", "/api/orders/" + id + "/cancel", Map.of());

    var order = orders.find(id).orElseThrow();
    assertEquals("CANCELLED", order.status());
    assertNull(order.employeeId());
    assertEquals(List.of("(new)->PENDING", "PENDING->CANCELLED"), trail(id));
    var last = events.byBooking(id).get(1);
    assertEquals("CUSTOMER", last.actorRole());
    assertEquals(users.byUsername("customer").orElseThrow().id(), last.actorId());
  }

  /** 迁移四之二：ASSIGNED → CANCELLED。已接单后被客户取消，负责人保留下来便于对账。 */
  @Test
  void cancelFromAssignedKeepsAssigneeButRecordsCancellation() throws Exception {
    var customer = login("customer");
    var employee = login("employee");
    long id = placed(customer, 1);
    long employeeId = users.byUsername("employee").orElseThrow().id();
    ok(employee, "POST", "/api/orders/" + id + "/claim", Map.of());

    ok(customer, "POST", "/api/orders/" + id + "/cancel", Map.of());

    var order = orders.find(id).orElseThrow();
    assertEquals("CANCELLED", order.status());
    assertEquals(employeeId, order.employeeId());
    assertEquals(List.of("(new)->PENDING", "PENDING->ASSIGNED", "ASSIGNED->CANCELLED"), trail(id));
  }

  /** 取消后时间线继续追加，不覆盖历史：退回再取消共 4 条，最后一条是 CANCELLED。 */
  @Test
  void cancelledOrderTimelineKeepsFullHistory() throws Exception {
    var customer = login("customer");
    var employee = login("employee");
    long id = placed(customer, 1);
    ok(employee, "POST", "/api/orders/" + id + "/claim", Map.of());
    ok(employee, "POST", "/api/orders/" + id + "/release", Map.of());

    ok(customer, "POST", "/api/orders/" + id + "/cancel", Map.of());

    var body = events.byBooking(id);
    assertEquals(4, body.size());
    assertEquals("CANCELLED", body.get(3).toStatus());
    assertEquals("PENDING", body.get(3).fromStatus());
  }

  /**
   * 状态与事件必须同生共死：事件写入失败时，接口报错且订单状态停在 ASSIGNED、事件仍是 2 条。
   *
   * <p>让仓储抛异常而不是改业务代码，是为了不留"临时抛异常"的痕迹又能长期回归这条约束。
   */
  @Test
  void failedEventWriteRollsBackStatusChange() throws Exception {
    var customer = login("customer");
    var employee = login("employee");
    long id = placed(customer, 1);
    ok(employee, "POST", "/api/orders/" + id + "/claim", Map.of());

    doThrow(new IllegalStateException("模拟事件写入失败"))
        .when(events)
        .append(anyLong(), any(), any(), anyLong(), any(), any());

    var failed = request(employee, "POST", "/api/orders/" + id + "/complete", Map.of());
    assertEquals(500, failed.getResponse().getStatus(), failed.getResponse().getContentAsString());

    assertEquals("ASSIGNED", orders.find(id).orElseThrow().status());
    assertEquals(2, events.byBooking(id).size());
  }

  /** 时间线的可见性与订单本身同一口径：看不到订单的人，也拿不到它的变更历史。 */
  @Test
  void timelineVisibilityFollowsOrderScope() throws Exception {
    var admin = login("admin");
    newUser(admin, "other_customer", "CUSTOMER");
    newUser(admin, "other_employee", "EMPLOYEE");
    var customer = login("customer");
    var otherCustomer = login("other_customer");
    var employee = login("employee");
    var otherEmployee = login("other_employee");
    long pending = placed(customer, 1);
    long assigned = placed(customer, 1);
    ok(employee, "POST", "/api/orders/" + assigned + "/claim", Map.of());

    // 下单客户与管理员：自己的订单、任意订单都能看。
    assertTimelineCount(customer, pending, 1);
    assertTimelineCount(admin, pending, 1);
    // 待接单是公共池，任何员工都能看到；已接单只对负责人和管理员开放。
    assertTimelineCount(otherEmployee, pending, 1);
    assertTimelineCount(employee, assigned, 2);
    assertTimelineCount(admin, assigned, 2);
    assertTimelineDenied(otherEmployee, assigned, 403);
    // 其他客户一律拒绝，未登录直接 401。
    assertTimelineDenied(otherCustomer, pending, 403);
    assertEquals(
        401,
        mvc.perform(get("/api/orders/" + pending + "/events"))
            .andReturn()
            .getResponse()
            .getStatus());
  }

  private void assertTimelineCount(Client c, long id, int count) throws Exception {
    var result = mvc.perform(get("/api/orders/" + id + "/events").session(c.session())).andReturn();
    assertEquals(200, result.getResponse().getStatus(), result.getResponse().getContentAsString());
    assertEquals(count, data(result).size());
  }

  private void assertTimelineDenied(Client c, long id, int expected) throws Exception {
    var result = mvc.perform(get("/api/orders/" + id + "/events").session(c.session())).andReturn();
    assertEquals(
        expected, result.getResponse().getStatus(), result.getResponse().getContentAsString());
  }
}
