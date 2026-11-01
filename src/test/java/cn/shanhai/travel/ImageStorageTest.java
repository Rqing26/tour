package cn.shanhai.travel;

import static org.junit.jupiter.api.Assertions.*;

import cn.shanhai.travel.service.ApiException;
import cn.shanhai.travel.service.ImageStorageService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Arrays;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

/**
 * 图片存储的校验与落盘规则（T4.2）。
 *
 * <p>全部用例直接调服务、用临时目录，不经过 HTTP：真正要钉住的是"看不看文件内容"以及"被拒时磁盘上是否干净"，
 * 上传接口与静态映射的行为在 T4.3、T4.4 之后再从接口层覆盖。
 */
class ImageStorageTest {

  @TempDir Path temp;
  private Path root;
  private ImageStorageService images;

  @BeforeEach
  void setUp() {
    // 目录故意设成两层且不存在，同时验证服务会自动建目录。
    root = temp.resolve("nested/uploads");
    images = new ImageStorageService(root.toString());
  }

  private static byte[] png() {
    return new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13};
  }

  private static byte[] jpeg() {
    return new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16, 'J', 'F'};
  }

  private static byte[] webp() {
    var bytes = new byte[16];
    System.arraycopy("RIFF".getBytes(StandardCharsets.US_ASCII), 0, bytes, 0, 4);
    System.arraycopy("WEBP".getBytes(StandardCharsets.US_ASCII), 0, bytes, 8, 4);
    return bytes;
  }

  private static byte[] zip() {
    return new byte[] {'P', 'K', 3, 4, 20, 0, 0, 0, 8, 0, 0, 0};
  }

  private MockMultipartFile file(String name, byte[] content) {
    return new MockMultipartFile("file", name, "application/octet-stream", content);
  }

  private long storedFiles() throws IOException {
    if (Files.notExists(root)) return 0;
    try (var list = Files.list(root)) {
      return list.count();
    }
  }

  private void assertRejected(HttpStatus status, MockMultipartFile file) throws IOException {
    var e = assertThrows(ApiException.class, () -> images.store(file));
    assertEquals(status, e.status());
    assertEquals(0, storedFiles(), "被拒的上传不能在磁盘上留残留文件");
  }

  @Test
  void storesPngUnderGeneratedNameAndCreatesMissingDirectory() throws IOException {
    var bytes = png();
    var name = images.store(file("我的行程.PNG", bytes));

    assertTrue(
        name.matches("[0-9a-f]{32}\\.png"), "存盘名必须是 UUID + 归一后的后缀，原始文件名不进文件系统");
    assertEquals(root.toAbsolutePath().normalize(), images.root(), "root() 供静态映射复用，须是归一后的绝对路径");
    var stored = root.resolve(name);
    assertTrue(Files.exists(stored), "文件应落在配置的根目录下");
    assertArrayEquals(bytes, Files.readAllBytes(stored));
    assertEquals(1, storedFiles());
  }

  @Test
  void acceptsJpegAndWebpWithNormalizedExtension() throws IOException {
    var jpegName = images.store(file("photo.JPEG", jpeg()));
    var webpName = images.store(file("photo.webp", webp()));

    assertTrue(jpegName.endsWith(".jpg"), "jpeg 后缀统一落成 jpg：" + jpegName);
    assertTrue(webpName.endsWith(".webp"), webpName);
    assertEquals(2, storedFiles());
  }

  @Test
  void rejectsZipRenamedToImageExtension() throws IOException {
    var e = assertThrows(ApiException.class, () -> images.store(file("trip.jpg", zip())));
    assertEquals(HttpStatus.BAD_REQUEST, e.status());
    assertTrue(e.getMessage().contains("文件内容不是图片"), e.getMessage());
    assertEquals(0, storedFiles(), "魔数不符时不能先写盘再删，磁盘必须干净");
  }

  /** 内容是真图但扩展名对不上（PNG 存成 .jpg）：同样按 400 拒，避免后缀与真实格式各说各话。 */
  @Test
  void rejectsWhenExtensionContradictsActualImageType() throws IOException {
    var e = assertThrows(ApiException.class, () -> images.store(file("trip.jpg", png())));
    assertEquals(HttpStatus.BAD_REQUEST, e.status());
    assertTrue(e.getMessage().contains("扩展名与实际内容不符"), e.getMessage());
    assertEquals(0, storedFiles());
  }

  @Test
  void rejectsImageContentWithUnsupportedExtension() throws IOException {
    var gif = "GIF89a".getBytes(StandardCharsets.US_ASCII);
    assertRejected(HttpStatus.BAD_REQUEST, file("trip.gif", gif));
    assertRejected(HttpStatus.BAD_REQUEST, file("trip", png()));
  }

  @Test
  void rejectsOversizeFileAsPayloadTooLarge() throws IOException {
    var bytes = Arrays.copyOf(png(), (int) ImageStorageService.MAX_BYTES + 1);
    assertRejected(HttpStatus.PAYLOAD_TOO_LARGE, file("huge.png", bytes));
  }

  @Test
  void rejectsEmptyAndNonImageContent() throws IOException {
    assertRejected(HttpStatus.BAD_REQUEST, file("empty.png", new byte[0]));
    assertRejected(
        HttpStatus.BAD_REQUEST, file("notes.png", "这不是图片".getBytes(StandardCharsets.UTF_8)));
  }
}
