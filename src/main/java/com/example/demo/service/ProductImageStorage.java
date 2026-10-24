package com.example.demo.service;

import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.imageio.*;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/** 外部ディレクトリで画像を管理し、元ファイル名や画像内の付加情報を公開しない。 */
@Service
public class ProductImageStorage {
    public static final String PREFIX = "/images/uploads/";
    public static final long MAX_BYTES = 5 * 1024 * 1024;
    private final Path directory;
    private final int maxFiles;

    public ProductImageStorage(
            @Value("${shop.images.directory:${user.dir}/data/product-images}") String directory,
            @Value("${shop.images.max-files:2000}") int maxFiles) {
        this.directory = Path.of(directory).toAbsolutePath().normalize();
        if (maxFiles < 1) throw new IllegalArgumentException("画像の保存上限は1件以上にしてください。");
        this.maxFiles = maxFiles;
    }

    public record Stored(String imageUrl, int width, int height, String format) {}

    /** 同時デコード数と保存上限の競合を抑え、完成したファイルだけを公開する。 */
    public synchronized Stored store(MultipartFile file) throws IOException {
        if (file.isEmpty() || file.getSize() > MAX_BYTES)
            throw invalid("画像は1件につき5 MB以下の空でないファイルを指定してください。");
        String original = Optional.ofNullable(file.getOriginalFilename()).orElse("");
        int dot = original.lastIndexOf('.');
        String extension = dot < 0 ? "" : original.substring(dot + 1).toLowerCase(Locale.ROOT);
        String expected = switch (extension) {
            case "jpg", "jpeg" -> "jpeg";
            case "png" -> "png";
            case "webp" -> "webp";
            default -> throw invalid("JPG・PNG・WebP の画像を指定してください。");
        };
        if (!("image/" + expected).equalsIgnoreCase(file.getContentType()))
            throw invalid("画像の種類とファイル形式が一致していません。");

        byte[] input;
        try (InputStream stream = file.getInputStream()) {
            input = stream.readNBytes((int) MAX_BYTES + 1);
        }
        if (input.length > MAX_BYTES) throw invalid("画像のサイズ上限は5 MBです。");

        BufferedImage decoded;
        try (var stream = new MemoryCacheImageInputStream(new ByteArrayInputStream(input))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) throw invalid("画像として読み取れないファイルです。");
            ImageReader reader = readers.next();
            try {
                reader.setInput(stream, false, true);
                String actual = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (actual.equals("jpg")) actual = "jpeg";
                if (!actual.equals(expected)) throw invalid("拡張子と実際の画像形式が一致していません。");
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width < 1 || height < 1 || width > 6000 || height > 6000
                        || (long) width * height > 12_000_000)
                    throw invalid("画像は各辺6000ピクセル以下、合計1200万画素以下にしてください。");
                if (reader.getNumImages(true) != 1) throw invalid("静止画像を1件指定してください。");
                AtomicBoolean warning = new AtomicBoolean();
                reader.addIIOReadWarningListener((source, message) -> warning.set(true));
                decoded = reader.read(0);
                if (decoded == null || warning.get()) throw invalid("破損した画像は使用できません。");
            } finally { reader.dispose(); }
        } catch (IOException | RuntimeException ex) {
            if (ex instanceof IllegalArgumentException invalid) throw invalid;
            throw invalid("画像を読み取れません。破損していない JPG・PNG・WebP を指定してください。");
        }

        // WebP は PNG に正規化し、元データの末尾やメタデータを引き継がない。
        String format = expected.equals("jpeg") ? "jpg" : "png";
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            if (!ImageIO.write(decoded, format, output)) throw new IOException("Image encoder unavailable");
        } finally { decoded.flush(); }
        if (output.size() > 16 * 1024 * 1024) throw invalid("変換後の画像が大きすぎます。画像を縮小してください。");

        Files.createDirectories(directory);
        try (var files = Files.list(directory)) {
            if (files.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)).count() >= maxFiles)
                throw invalid("画像の保存上限に達しました。管理者に保存領域の確認を依頼してください。");
        }
        String name = UUID.randomUUID().toString().replace("-", "") + "." + format;
        Path destination = directory.resolve(name);
        Path temporary = Files.createTempFile(directory, ".upload-", ".tmp");
        try {
            Files.write(temporary, output.toByteArray());
            Files.move(temporary, destination);
        } finally { Files.deleteIfExists(temporary); }
        return new Stored(PREFIX + name, decoded.getWidth(), decoded.getHeight(), format);
    }

    /** 公開可能な一意ファイル名だけを受け付け、階層移動とシンボリックリンクを拒否する。 */
    public Optional<Path> find(String path) {
        if (path == null || !path.matches("/images/uploads/[0-9a-f]{32}\\.(jpg|png)")) return Optional.empty();
        Path file = directory.resolve(path.substring(PREFIX.length()));
        return Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) ? Optional.of(file) : Optional.empty();
    }

    private static IllegalArgumentException invalid(String message) { return new IllegalArgumentException(message); }
}
