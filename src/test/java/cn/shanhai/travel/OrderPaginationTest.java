package cn.shanhai.travel;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import cn.shanhai.travel.dto.Api;
import cn.shanhai.travel.repository.OrderRepository.Filter;
import cn.shanhai.travel.repository.OrderRepository.Sort;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * T3 分页与索引下推用例：先锁索引与分页契约，再验证三种角色的可见性是否真的在 SQL 里收窄。
 *
 * <p>公共装置见 {@link AbstractApiTest}；每个用例的数据由基类重置，互不依赖执行顺序。
 */
class OrderPaginationTest extends AbstractApiTest {
  /** 分页要能用上索引，索引列的顺序必须与"过滤列 + 排序列"一致，否则深翻页仍会回表排序。 */
  @Test
  void bookingTableHasIndexesMatchingPagedQueryShapes() {
    var indexes = indexNames("bookings");

    assertTrue(indexes.contains("idx_booking_customer_time"), "缺少客户分页索引：" + indexes);
    assertTrue(indexes.contains("idx_booking_status_time"), "缺少状态分页索引：" + indexes);
    assertTrue(indexes.contains("idx_booking_employee_time"), "缺少员工分页索引：" + indexes);

    // 列顺序即索引顺序：先按归属过滤，再按创建时间排序，最后用自增 id 兜底保证翻页稳定。
    assertEquals(
        List.of("customer_id", "created_at", "id"), indexColumns("idx_booking_customer_time"));
    assertEquals(List.of("status", "created_at", "id"), indexColumns("idx_booking_status_time"));
    assertEquals(
        List.of("employee_id", "created_at", "id"), indexColumns("idx_booking_employee_time"));
  }

  /** 分页是"窗口"而不是"整表"：相邻两页不重叠、不漏行，且顺序按创建时间倒序、id 兜底。 */
  @Test
  void pageReturnsStableNonOverlappingWindow() throws Exception {
    var customer = login("customer");
    for (int i = 0; i < 5; i++) createOrder(customer, route(10), 1);

    var all = orders.page(Filter.ALL, Sort.DEFAULT, 0, 100);
    var first = orders.page(Filter.ALL, Sort.DEFAULT, 0, 3);
    var second = orders.page(Filter.ALL, Sort.DEFAULT, 3, 3);

    assertEquals(3, first.size(), "首页应恰好是一页的容量");
    assertEquals(ids(all).subList(0, 3), ids(first));
    assertEquals(ids(all).subList(3, 6), ids(second));
    assertTrue(Collections.disjoint(ids(first), ids(second)), "相邻页不能重复出现同一订单");
    for (int i = 1; i < all.size(); i++) {
      var prev = all.get(i - 1);
      var curr = all.get(i);
      assertTrue(
          prev.createdAt().isAfter(curr.createdAt())
              || (prev.createdAt().isEqual(curr.createdAt()) && prev.id() > curr.id()),
          "排序应为 created_at 倒序、id 倒序兜底");
    }
  }

  /** 总数与当前页必须出自同一套过滤条件，否则前端会算出多一页空列表。 */
  @Test
  void countMatchesFilteredRowsNotWholeTable() throws Exception {
    var customer = login("customer");
    long mine = createOrder(customer, route(10), 1);

    assertEquals(orders.page(Filter.ALL, Sort.DEFAULT, 0, 1000).size(), orders.count(Filter.ALL));
    long customerId = orders.find(mine).orElseThrow().customerId();
    var onlyMine = new Filter(customerId, null, null, null);
    assertEquals(orders.page(onlyMine, Sort.DEFAULT, 0, 1000).size(), orders.count(onlyMine));
    assertTrue(orders.count(onlyMine) < orders.count(Filter.ALL), "客户范围必须小于管理员范围");
    // 状态与归属叠加后，命中集合只可能是这笔刚下的 PENDING 订单。
    var orderNo = orders.find(mine).orElseThrow().orderNo();
    assertEquals(1, orders.count(new Filter(customerId, null, "PENDING", orderNo)));
    assertEquals(0, orders.count(new Filter(customerId, null, "CANCELLED", orderNo)));
  }

  /** 翻到超出末页的位置返回空列表，而不是报错或回退到第一页。 */
  @Test
  void pagePastTheEndReturnsEmptyList() {
    long total = orders.count(Filter.ALL);
    assertTrue(total > 0);

    assertTrue(orders.page(Filter.ALL, Sort.DEFAULT, (int) total, 10).isEmpty());
    assertEquals(total, orders.count(Filter.ALL), "翻过末页不能影响总数");
  }

  /** 关键词按字面匹配订单号、线路名、客户名、联系人与负责人，大小写不敏感。 */
  @Test
  void keywordMatchesOrderNumberAndNames() throws Exception {
    var customer = login("customer");
    long id = createOrder(customer, route(10), 1);
    var orderNo = orders.find(id).orElseThrow().orderNo();

    assertEquals(
        List.of(id), ids(orders.page(new Filter(null, null, null, orderNo), Sort.DEFAULT, 0, 50)));
    assertEquals(
        List.of(id),
        ids(orders.page(new Filter(null, null, null, orderNo.toLowerCase()), Sort.DEFAULT, 0, 50)));
    assertEquals(
        1,
        orders.page(new Filter(null, null, null, "测试联系人"), Sort.DEFAULT, 0, 50).size(),
        "联系人姓名也应参与匹配");
  }

  /** LIKE 的通配符必须被转义，否则搜 "%" 会把整张表当成命中。 */
  @Test
  void keywordTreatsLikeWildcardsLiterally() {
    assertTrue(orders.count(Filter.ALL) > 0);

    assertEquals(0, orders.count(new Filter(null, null, null, "%")));
    assertEquals(0, orders.count(new Filter(null, null, null, "_")));
  }

  /** 管理员不受限制：所有客户、所有状态的订单都在同一页里，总数与行数一致。 */
  @Test
  void adminSeesEveryOrder() throws Exception {
    var admin = login("admin");

    var list = orderList(admin);

    assertEquals(orders.count(Filter.ALL), list.size());
    assertTrue(list.size() > 1);
  }

  /** 客户只能看到自己的订单，别人的订单一律不出现，且总数也不会把别人的算进来。 */
  @Test
  void customerSeesOnlyOwnOrders() throws Exception {
    var admin = login("admin");
    newUser(admin, "other_customer", "CUSTOMER");
    var customer = login("customer");
    var other = login("other_customer");
    long mine = createOrder(customer, route(10), 1);
    long theirs = createOrder(other, route(10), 1);

    var own = ids(orderList(customer));
    assertTrue(own.contains(mine));
    assertFalse(own.contains(theirs));
    assertEquals(List.of(theirs), ids(orderList(other)), "对方只应有自己刚下的那一笔");
  }

  /**
   * 员工的可见范围是"待接池 + 自己名下"：别人接走的订单看不到，待接单则全员可见。
   *
   * <p>这条口径决定 SQL 里必须写成 {@code status='PENDING' OR employee_id=?}，不能只按客户那样等值过滤。
   */
  @Test
  void employeeSeesPendingPoolAndOwnAssignedOnly() throws Exception {
    var admin = login("admin");
    newUser(admin, "second_employee", "EMPLOYEE");
    var owner = login("employee");
    var peer = login("second_employee");
    long claimed = createOrder(login("customer"), route(10), 1);
    long pending = createOrder(login("customer"), route(10), 1);
    ok(owner, "POST", "/api/orders/" + claimed + "/claim", Map.of());

    var ownerView = ids(orderList(owner));
    var peerView = ids(orderList(peer));

    assertTrue(ownerView.contains(claimed), "负责人应看到自己接的订单");
    assertTrue(ownerView.contains(pending), "待接单对全员可见");
    assertFalse(peerView.contains(claimed), "别人接走的订单不应出现在自己的列表里");
    assertTrue(peerView.contains(pending));
  }

  /** 状态与关键词在可见范围内继续收窄，不能因为下推就漏掉其中一层条件。 */
  @Test
  void statusAndKeywordFilterWithinVisibleScope() throws Exception {
    var customer = login("customer");
    long id = createOrder(customer, route(10), 1);
    var orderNo = orders.find(id).orElseThrow().orderNo();

    assertEquals(List.of(id), ids(orderList(customer, "?status=PENDING&q=" + orderNo)));
    assertEquals(0, orderList(customer, "?status=COMPLETED&q=" + orderNo).size());
    // 无效状态沿用原有 400 契约，不能被下推悄悄变成空结果。
    assertEquals(
        400,
        mvc.perform(get("/api/orders?status=UNKNOWN").session(customer.session()))
            .andReturn()
            .getResponse()
            .getStatus());
  }

  /** 接口契约：带分页参数时返回信封 {@code {items,total,page,size}}，total 是过滤后的全量行数。 */
  @Test
  void pagedRequestReturnsEnvelopeWithItemsAndTotal() throws Exception {
    var customer = login("customer");
    long route = route(100);
    for (int i = 0; i < 12; i++) createOrder(customer, route, 1);

    var body = envelope(login("admin"), "?page=0&size=5");

    assertEquals(5, body.get("items").size(), "首页应恰好装满一页");
    assertTrue(body.get("total").asLong() >= 12, "total 是过滤后的全量行数，不是当前页条数");
    assertEquals(0, body.get("page").asInt());
    assertEquals(5, body.get("size").asInt());
    assertEquals(
        body.get("total").asLong(), orderList(login("admin")).size(), "信封里的 total 应与整表口径一致");
  }

  /** 页大小上限在服务端兜底：前端传 1000，响应里必须回显真正生效的 100。 */
  @Test
  void pageSizeAboveLimitFallsBackToHundred() throws Exception {
    var body = envelope(login("admin"), "?page=0&size=1000");

    assertEquals(100, body.get("size").asInt(), "size 超限应被服务端压到 100");
    assertTrue(body.get("items").size() <= 100);
    assertEquals(0, body.get("page").asInt());
  }

  /** 不带分页参数时仍是原来的数组：C4 要求已有调用方（前端旧代码、冒烟脚本）不被打断。 */
  @Test
  void requestWithoutPagingParamsKeepsArrayContract() throws Exception {
    var result =
        mvc.perform(get("/api/orders").session(login("admin").session())).andReturn();

    assertEquals(200, result.getResponse().getStatus());
    var node = json.readTree(result.getResponse().getContentAsString());
    assertTrue(node.isArray(), "未传 page/size 时应保持数组响应，不能变成信封");
    assertEquals(orderList(login("admin")).size(), node.size());
  }

  /** 排序键只接受白名单，拼错的键要报 400，而不是悄悄回退成默认排序。 */
  @Test
  void invalidSortKeyIsRejectedWithBadRequest() throws Exception {
    var result =
        mvc.perform(get("/api/orders?page=0&size=5&sort=b.created_at").session(login("admin").session()))
            .andReturn();

    assertEquals(400, result.getResponse().getStatus());
    assertTrue(result.getResponse().getContentAsString().contains("排序"));
    var direction =
        mvc.perform(get("/api/orders?page=0&size=5&sort=createdAt,sideways").session(login("admin").session()))
            .andReturn();
    assertEquals(400, direction.getResponse().getStatus());
  }

  /** 翻页在 HTTP 层也不重不漏：第二页与第一页无交集，且页内顺序连续。 */
  @Test
  void consecutivePagesDoNotOverlapOverHttp() throws Exception {
    var customer = login("customer");
    long route = route(100);
    for (int i = 0; i < 8; i++) createOrder(customer, route, 1);
    var admin = login("admin");

    var first = idsOf(envelope(admin, "?page=0&size=4"));
    var second = idsOf(envelope(admin, "?page=1&size=4"));

    assertEquals(4, first.size());
    assertEquals(4, second.size());
    assertTrue(Collections.disjoint(first, second), "相邻页不能重复出现同一订单");
    assertEquals(
        ids(orderList(admin)).subList(0, 8), concat(first, second), "两页拼起来应与整表前 8 条一致");
  }

  private JsonNode envelope(Client c, String query) throws Exception {
    var result = mvc.perform(get("/api/orders" + query).session(c.session())).andReturn();
    assertEquals(200, result.getResponse().getStatus(), result.getResponse().getContentAsString());
    var node = json.readTree(result.getResponse().getContentAsString());
    assertTrue(node.isObject(), "带分页参数时应返回 {items,total,page,size} 信封");
    return node;
  }

  private List<Long> idsOf(JsonNode envelope) {
    var list = new ArrayList<Long>();
    envelope.get("items").forEach(item -> list.add(item.get("id").asLong()));
    return list;
  }

  private List<Long> concat(List<Long> first, List<Long> second) {
    var all = new ArrayList<>(first);
    all.addAll(second);
    return all;
  }

  /** H2（DATABASE_TO_LOWER）与 MySQL 的元数据大小写不一致，统一转小写后比对。 */
  private Set<String> indexNames(String table) {
    return new HashSet<>(
        jdbc.queryForList(
            "SELECT LOWER(index_name) FROM information_schema.indexes WHERE LOWER(table_name)=?",
            String.class,
            table));
  }

  private List<String> indexColumns(String index) {
    return jdbc.queryForList(
        "SELECT LOWER(column_name) FROM information_schema.index_columns"
            + " WHERE LOWER(index_name)=? ORDER BY ordinal_position",
        String.class,
        index);
  }

  private List<Long> ids(List<Api.Order> orders) {
    return orders.stream().map(Api.Order::id).toList();
  }

  private List<Api.Order> orderList(Client c) throws Exception {
    return orderList(c, "");
  }

  private List<Api.Order> orderList(Client c, String query) throws Exception {
    var result = mvc.perform(get("/api/orders" + query).session(c.session())).andReturn();
    assertEquals(200, result.getResponse().getStatus(), result.getResponse().getContentAsString());
    return json.readValue(
        result.getResponse().getContentAsString(),
        json.getTypeFactory().constructCollectionType(List.class, Api.Order.class));
  }
}
