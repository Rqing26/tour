package cn.shanhai.travel;

import static org.junit.jupiter.api.Assertions.*;

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
    assertEquals(List.of("customer_id", "created_at", "id"), indexColumns("idx_booking_customer_time"));
    assertEquals(List.of("status", "created_at", "id"), indexColumns("idx_booking_status_time"));
    assertEquals(List.of("employee_id", "created_at", "id"), indexColumns("idx_booking_employee_time"));
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
}
