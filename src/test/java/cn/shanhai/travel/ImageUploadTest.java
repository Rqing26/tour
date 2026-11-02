package cn.shanhai.travel;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * 线路图片上传的表结构基线（T4.1）与上传接口契约（T4.3）。
 *
 * <p>上传功能把新图落到磁盘、表里只记相对文件名，因此列必须可空：旧线路没有上传过图片，`image_path` 为空要能正常读出。
 * `image_key` 是 6 个固定枚举图片的映射键，保留 `NOT NULL`，前端在 `image_path` 为空时回退到它（C7）。
 */
class ImageUploadTest extends AbstractApiTest {

  /** 只有文件头，够魔数判定用；真实图片的像素内容与"是不是图片"无关。 */
  private static final byte[] PNG_HEAD = {
    (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D, 'I', 'H', 'D', 'R'
  };

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

  private MockMultipartFile png(String filename) {
    return new MockMultipartFile("file", filename, "image/png", PNG_HEAD);
  }

  private MvcResult upload(Client c, long id, MockMultipartFile file) throws Exception {
    return mvc.perform(
            MockMvcRequestBuilders.multipart("/api/routes/" + id + "/image")
                .file(file)
                .session(c.session())
                .header("X-CSRF-TOKEN", c.csrf()))
        .andReturn();
  }

  /** 列表按出发日期排序，造出来的线路不一定在首位，按 id 取。 */
  private JsonNode listedRoute(JsonNode routes, long id) {
    for (var r : routes) if (r.get("id").asLong() == id) return r;
    throw new AssertionError("线路 " + id + " 不在列表里");
  }

  @Test
  void adminUploadStoresFileNameAndKeepsImageKey() throws Exception {
    var admin = login("admin");
    long id = route(20);

    var r = upload(admin, id, png("桂林 封面.PNG"));

    assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
    var stored = data(r).get("imagePath").asText();
    assertTrue(
        stored.matches("[0-9a-f]{32}\\.png"), "存盘名应是 UUID + 归一后缀，实际为 " + stored);
    assertEquals("guilin", data(r).get("imageKey").asText(), "上传不得覆盖内置图片键（C7）");
    assertEquals(
        stored,
        jdbc.queryForObject("SELECT image_path FROM travel_routes WHERE id=?", String.class, id),
        "表里只记文件名，不记目录");
  }

  @Test
  void uploadedRouteReadsBackWithImagePath() throws Exception {
    var admin = login("admin");
    long id = route(20);
    var stored = data(upload(admin, id, png("cover.png"))).get("imagePath").asText();

    var detail = mvc.perform(MockMvcRequestBuilders.get("/api/routes/" + id).session(admin.session()))
        .andExpect(status().isOk()).andReturn();
    assertEquals(stored, data(detail).get("imagePath").asText());

    var list = mvc.perform(MockMvcRequestBuilders.get("/api/routes").session(admin.session()))
        .andExpect(status().isOk()).andReturn();
    var row = listedRoute(data(list), id);
    assertEquals(stored, row.get("imagePath").asText());
  }

  @Test
  void nonAdminCannotUpload() throws Exception {
    var employee = login("employee");
    long id = route(20);

    var r = upload(employee, id, png("cover.png"));

    assertEquals(403, r.getResponse().getStatus(), r.getResponse().getContentAsString());
    assertNull(
        jdbc.queryForObject("SELECT image_path FROM travel_routes WHERE id=?", String.class, id),
        "被拒的上传不得写库");
  }

  @Test
  void uploadedImageIsServedUnderUploadsPath() throws Exception {
    var admin = login("admin");
    long id = route(20);
    var stored = data(upload(admin, id, png("cover.png"))).get("imagePath").asText();

    var r =
        mvc.perform(MockMvcRequestBuilders.get("/uploads/" + stored))
            .andExpect(status().isOk())
            .andReturn();

    assertArrayEquals(PNG_HEAD, r.getResponse().getContentAsByteArray());
    assertTrue(
        r.getResponse().getContentType().startsWith("image/png"),
        "静态映射要带上图片的真实类型，否则浏览器会当成下载");
  }

  @Test
  void uploadsPathDoesNotEscapeItsRoot() throws Exception {
    mvc.perform(MockMvcRequestBuilders.get("/uploads/not-exists.png"))
        .andExpect(status().isNotFound());

    // 带 `..` 的路径在进入资源解析前就被判为非法（400），不存在的文件是 404——两种都算拒答，
    // 这里断言的是"绝不把根目录外的文件读出来"，而不是具体哪个状态码。
    var escaped = mvc.perform(MockMvcRequestBuilders.get("/uploads/../application.yml")).andReturn();
    assertNotEquals(200, escaped.getResponse().getStatus(), "越界路径不得被当成文件读出");
    assertFalse(
        escaped.getResponse().getContentAsString().contains("spring"),
        "响应里不能出现上传根目录以外的文件内容");
  }

  @Test
  void uploadToMissingRouteIsNotFound() throws Exception {
    var admin = login("admin");

    var r = upload(admin, 999999L, png("cover.png"));

    assertEquals(404, r.getResponse().getStatus(), r.getResponse().getContentAsString());
  }
}
