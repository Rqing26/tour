package cn.shanhai.travel;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.*;

/**
 * 线路图片上传的表结构基线（T4.1）。
 *
 * <p>上传功能把新图落到磁盘、表里只记相对文件名，因此列必须可空：旧线路没有上传过图片，`image_path` 为空要能正常读出。
 * `image_key` 是 6 个固定枚举图片的映射键，保留 `NOT NULL`，前端在 `image_path` 为空时回退到它（C7）。
 */
class ImageUploadTest extends AbstractApiTest {

  private Map<String, Object> column(String table, String name) {
    var rows =
        jdbc.queryForList(
            "SELECT column_name, is_nullable, character_maximum_length FROM"
                + " information_schema.columns WHERE LOWER(table_name)=? AND LOWER(column_name)=?",
            table,
            name);
    assertEquals(1, rows.size(), table + " 表缺少列 " + name);
    return rows.getFirst();
  }

  @Test
  void imagePathIsOptionalWhileImageKeyStaysRequired() {
    var path = column("travel_routes", "image_path");
    assertEquals("YES", path.get("is_nullable"), "image_path 必须可空，旧线路没有上传过图片");
    assertEquals(200L, ((Number) path.get("character_maximum_length")).longValue());

    assertEquals("NO", column("travel_routes", "image_key").get("is_nullable"), "image_key 仍是回退来源");
  }

  @Test
  void existingRoutesStayReadableWithoutImagePath() {
    assertTrue(
        jdbc.queryForObject(
                "SELECT COUNT(*) FROM travel_routes WHERE image_path IS NULL", Integer.class)
            > 0,
        "演示数据没有上传过图片，应保留 image_path 为空而不是写入占位值");
    assertEquals(
        0,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM travel_routes WHERE image_key IS NULL", Integer.class));
  }
}
