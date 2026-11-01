package cn.shanhai.travel.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * 线路图片落盘。
 *
 * <p>判断"是不是图片"只看文件头魔数，不看扩展名：扩展名只决定存盘后缀，把 zip 改名成 .jpg 会在这里被拒。
 * 存盘文件名由 UUID 生成，原始文件名一律丢弃——避免中文名、路径分隔符、`..` 这类输入进入文件系统。
 * 表里只存这个文件名（{@code travel_routes.image_path}），对外访问路径由静态资源映射负责（T4.4）。
 */
@Service
public class ImageStorageService {
  /** 单张图片上限 5MB：既是业务约束，也是上传接口 413 的判定线。 */
  public static final long MAX_BYTES = 5L * 1024 * 1024;

  /** JPEG 的 SOI 只有 3 字节，WebP 的 "WEBP" 标记在第 9–12 字节，取 12 字节即可覆盖三种格式。 */
  private static final int HEAD_BYTES = 12;

  private static final Set<String> EXTENSIONS = Set.of("png", "jpg", "jpeg", "webp");

  private static final byte[] PNG_SIGNATURE = {
    (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A
  };

  private final Path root;

  public ImageStorageService(@Value("${travel.upload.dir}") String directory) {
    this.root = Paths.get(directory).toAbsolutePath().normalize();
  }

  /** 图片根目录：静态资源映射（T4.4）与本服务共用同一个解析结果，避免两处各拼一次相对路径。 */
  public Path root() {
    return root;
  }

  /**
   * 校验并保存一张图片。
   *
   * @return 存盘文件名（不含目录），调用方把它写进 {@code travel_routes.image_path}
   */
  public String store(MultipartFile file) {
    if (file == null || file.isEmpty()) throw ApiException.invalid("请选择要上传的图片");
    if (file.getSize() > MAX_BYTES) throw ApiException.tooLarge("图片不能超过 5MB");
    var extension = extension(file.getOriginalFilename());
    var detected = detect(head(file));
    if (detected == null) throw ApiException.invalid("文件内容不是图片，仅支持 PNG、JPEG、WebP");
    if (!detected.equals(family(extension)))
      throw ApiException.invalid("文件扩展名与实际内容不符，请上传真正的 PNG、JPEG 或 WebP 图片");
    var name = UUID.randomUUID().toString().replace("-", "") + "." + extension;
    try {
      Files.createDirectories(root);
      try (InputStream in = file.getInputStream()) {
        Files.copy(in, root.resolve(name));
      }
    } catch (IOException e) {
      throw new IllegalStateException("图片保存失败", e);
    }
    return name;
  }

  /**
   * 取扩展名并归一（{@code .JPEG} → {@code jpg}）：大小写与 {@code jpeg}/{@code jpg} 两种写法都算合法，
   * 不在白名单里的直接拒绝，不让它走到写盘那一步。
   */
  private static String extension(String filename) {
    var name = filename == null ? "" : filename.trim();
    var dot = name.lastIndexOf('.');
    var raw = dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    if (!EXTENSIONS.contains(raw)) throw ApiException.invalid("仅支持 PNG、JPEG、WebP 图片");
    return raw.equals("jpeg") ? "jpg" : raw;
  }

  /** 同一格式的不同后缀归到一族，用于与魔数比对。 */
  private static String family(String extension) {
    return switch (extension) {
      case "png" -> "png";
      case "jpg" -> "jpeg";
      case "webp" -> "webp";
      default -> "";
    };
  }

  private static byte[] head(MultipartFile file) {
    try (InputStream in = file.getInputStream()) {
      return in.readNBytes(HEAD_BYTES);
    } catch (IOException e) {
      throw new IllegalStateException("图片读取失败", e);
    }
  }

  /** @return {@code png} / {@code jpeg} / {@code webp}，无法识别时返回 {@code null} */
  private static String detect(byte[] head) {
    if (startsWith(head, PNG_SIGNATURE)) return "png";
    if (head.length >= 3
        && (head[0] & 0xFF) == 0xFF
        && (head[1] & 0xFF) == 0xD8
        && (head[2] & 0xFF) == 0xFF) return "jpeg";
    if (startsWith(head, "RIFF".getBytes(StandardCharsets.US_ASCII), 0)
        && startsWith(head, "WEBP".getBytes(StandardCharsets.US_ASCII), 8)) return "webp";
    return null;
  }

  private static boolean startsWith(byte[] data, byte[] prefix) {
    return startsWith(data, prefix, 0);
  }

  private static boolean startsWith(byte[] data, byte[] prefix, int offset) {
    if (data.length < offset + prefix.length) return false;
    return Arrays.equals(data, offset, offset + prefix.length, prefix, 0, prefix.length);
  }
}
