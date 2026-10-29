package cn.shanhai.travel;

import static org.junit.jupiter.api.Assertions.*;

import cn.shanhai.travel.dto.Api;
import cn.shanhai.travel.repository.OrderRepository.Filter;
import cn.shanhai.travel.repository.OrderRepository.Sort;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
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
}
