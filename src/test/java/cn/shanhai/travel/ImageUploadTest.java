package cn.shanhai.travel;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cn.shanhai.travel.service.ImageStorageService;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * 线路图片上传的表结构基线（T4.1）、上传接口契约（T4.3）与接口层安全边界（T4.6）。
 *
 * <p>上传功能把新图落到磁盘、表里只记相对文件名，因此列必须可空：旧线路没有上传过图片，`image_path` 为空要能正常读出。
 * `image_key` 是 6 个固定枚举图片的映射键，保留 `NOT NULL`，前端在 `image_path` 为空时回退到它（C7）。
 *
 * <p>T4.6 只从接口层钉"被拒之后什么都没发生"：状态码、磁盘条目数、`image_path` 三者必须同时成立。
 * 只断 400 而不查磁盘，等于放过"文件已经落盘、库没写"的脏状态。
 */
class ImageUploadTest extends AbstractApiTest {
  @Autowired private ImageStorageService images;

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
    assertNull(imagePathOf(id), "被拒的上传不得写库");
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

  /** 上传根目录由 `travel.upload.dir` 决定（测试 profile 指向 target/test-uploads），与静态映射共用同一个解析结果。 */
  private Path uploadRoot() {
    return images.root();
  }

  private long storedFiles() throws IOException {
    var root = uploadRoot();
    if (Files.notExists(root)) return 0;
    try (var list = Files.list(root)) {
      return list.count();
    }
  }

  private String imagePathOf(long id) {
    return jdbc.queryForObject("SELECT image_path FROM travel_routes WHERE id=?", String.class, id);
  }

  /**
   * 被拒的上传必须"三无"：状态码符合预期、磁盘条目数不变、`image_path` 仍为空。
   *
   * <p>磁盘只数条目数而不看文件名，是因为服务端用 UUID 命名，被拒时根本不该产生任何条目；
   * 只断状态码会漏掉"先写盘再校验"的实现。
   */
  private void assertRejected(MockMultipartFile file, int status, String message) throws Exception {
    var admin = login("admin");
    long id = route(20);
    long before = storedFiles();

    var r = upload(admin, id, file);

    assertEquals(status, r.getResponse().getStatus(), r.getResponse().getContentAsString());
    assertTrue(
        data(r).get("message").asText().contains(message),
        "错误信息应说明原因，实际为 " + data(r).get("message").asText());
    assertEquals(before, storedFiles(), "被拒的上传不能在磁盘上留残留文件");
    assertNull(imagePathOf(id), "被拒的上传不得写库");
  }

  /** 压缩包改名成 .jpg：扩展名过白名单，但文件头不是任何图片格式。 */
  @Test
  void rejectsArchiveRenamedToImage() throws Exception {
    var zip = new byte[] {'P', 'K', 3, 4, 20, 0, 0, 0, 8, 0, 0, 0};

    assertRejected(
        new MockMultipartFile("file", "行程封面.jpg", "image/jpeg", zip), 400, "文件内容不是图片");
  }

  /** 内容是真 PNG 但后缀写成 .jpg：不因为"内容合法"就放行，否则库里后缀与真实格式各说各话。 */
  @Test
  void rejectsContentContradictingExtension() throws Exception {
    assertRejected(png("行程封面.jpg"), 400, "扩展名与实际内容不符");
  }

  /** 超出 5MB 上限走 413 而不是 400：前端要能区分"太大"与"格式不对"，提示文案不同。 */
  @Test
  void rejectsSixMegabyteUpload() throws Exception {
    var huge = new byte[6 * 1024 * 1024];
    System.arraycopy(PNG_HEAD, 0, huge, 0, PNG_HEAD.length);

    assertRejected(new MockMultipartFile("file", "huge.png", "image/png", huge), 413, "图片不能超过 5MB");
  }

  @Test
  void rejectsEmptyUpload() throws Exception {
    assertRejected(
        new MockMultipartFile("file", "empty.png", "image/png", new byte[0]), 400, "请选择要上传的图片");
  }

  /** 部件名写错（`image` 而非 `file`）按缺参处理，不能落到 500。 */
  @Test
  void rejectsRequestWithoutFilePart() throws Exception {
    var admin = login("admin");
    long id = route(20);
    long before = storedFiles();

    var r =
        mvc.perform(
                MockMvcRequestBuilders.multipart("/api/routes/" + id + "/image")
                    .file(new MockMultipartFile("image", "cover.png", "image/png", PNG_HEAD))
                    .session(admin.session())
                    .header("X-CSRF-TOKEN", admin.csrf()))
            .andReturn();

    assertEquals(400, r.getResponse().getStatus(), r.getResponse().getContentAsString());
    assertTrue(data(r).get("message").asText().contains("请选择要上传的图片"));
    assertEquals(before, storedFiles());
    assertNull(imagePathOf(id));
  }

  /**
   * 原始文件名里的路径分隔符与 `..` 必须被丢弃。
   *
   * <p>服务端只取扩展名、用 UUID 重新命名，所以四种"想跳出去"的写法都应正常上传、且文件落在根目录内；
   * 反向的写法（直接把原始名拼进路径）会让文件出现在根目录之外，这里的 `getParent()` 断言就是拦它。
   */
  @Test
  void originalFileNameCannotEscapeUploadRoot() throws Exception {
    var admin = login("admin");
    long id = route(20);
    var root = uploadRoot();
    long before = storedFiles();

    for (var name :
        List.of(
            "../../../evil.png", "..\\..\\evil.png", "C:\\Windows\\evil.png", "/etc/evil.png")) {
      var r = upload(admin, id, png(name));
      assertEquals(
          200, r.getResponse().getStatus(), name + " → " + r.getResponse().getContentAsString());

      var stored = data(r).get("imagePath").asText();
      assertTrue(stored.matches("[0-9a-f]{32}\\.png"), "原始文件名应被丢弃，实际为 " + stored);
      var path = root.resolve(stored).normalize();
      assertEquals(root, path.getParent(), "文件必须直接落在上传根目录下，不能借文件名跳出去");
      assertTrue(Files.exists(path), "上传成功的文件必须真实存在：" + path);
    }

    assertEquals(before + 4, storedFiles(), "四次上传各落一个文件，目录外不得多出任何条目");
    assertFalse(Files.exists(root.resolve("evil.png")), "原始文件名不得成为磁盘上的文件名");
  }

  /** 游客未登录：先被 CSRF 与会话拦下，返回 401 而不是 403，前端据此跳登录页。 */
  @Test
  void anonymousCannotUpload() throws Exception {
    var guest = anonymous();
    long id = route(20);
    long before = storedFiles();

    var r =
        mvc.perform(
                MockMvcRequestBuilders.multipart("/api/routes/" + id + "/image")
                    .file(png("cover.png"))
                    .session(guest.session())
                    .header("X-CSRF-TOKEN", guest.csrf()))
            .andReturn();

    assertEquals(401, r.getResponse().getStatus(), r.getResponse().getContentAsString());
    assertEquals(before, storedFiles());
    assertNull(imagePathOf(id));
  }

  @Test
  void customerCannotUpload() throws Exception {
    var customer = login("customer");
    long id = route(20);
    long before = storedFiles();

    var r = upload(customer, id, png("cover.png"));

    assertEquals(403, r.getResponse().getStatus(), r.getResponse().getContentAsString());
    assertEquals(before, storedFiles());
    assertNull(imagePathOf(id));
  }
}
