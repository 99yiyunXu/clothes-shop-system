package com.example.demo;

import com.example.demo.entity.Product;
import com.example.demo.repository.ProductRepository;
import com.example.demo.service.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.*;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 商品データは専用 H2、画像はテスト専用ディレクトリに隔離する。 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:luna_upload_test;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProductImageUploadTests {
    @TempDir static Path directory;
    @DynamicPropertySource
    static void settings(DynamicPropertyRegistry registry) {
        registry.add("shop.images.directory", () -> directory.toString());
    }
    @Autowired MockMvc mvc;
    @Autowired ProductRepository products;
    @Autowired ProductImageService images;
    Product existing;

    @BeforeEach
    void prepare() {
        products.deleteAll();
        existing = new Product(); existing.setName("既存ニット"); existing.setPrice(2400);
        existing.setStock(10); existing.setCategory("レディース");
        existing.setImageUrl("/images/products/knit1.png");
        existing = products.saveAndFlush(existing);
    }

    private byte[] bitmap(String format) throws IOException {
        var output = new ByteArrayOutputStream();
        var image = new BufferedImage(30, 40, BufferedImage.TYPE_INT_RGB);
        assertThat(ImageIO.write(image, format, output)).isTrue();
        return output.toByteArray();
    }
    private MockMultipartFile png() throws IOException {
        return new MockMultipartFile("file", "original.png", "image/png", bitmap("png"));
    }
    private String upload(MockMultipartFile file) throws Exception {
        var result = mvc.perform(multipart("/admin/product-images").file(file)
                .with(user("manager").roles("ADMIN")).with(csrf()))
                .andExpect(status().isCreated()).andReturn();
        // 機械生成されたパスだけを抽出し、JSON ライブラリへの追加依存を避ける。
        var matcher = java.util.regex.Pattern.compile("/images/uploads/[0-9a-f]{32}\\.(png|jpg)")
                .matcher(result.getResponse().getContentAsString());
        assertThat(matcher.find()).isTrue();
        return matcher.group();
    }
    private void unchanged() {
        Product saved = products.findById(existing.getId()).orElseThrow();
        assertThat(products.count()).isEqualTo(1);
        assertThat(saved.getImageUrl()).isEqualTo("/images/products/knit1.png");
        assertThat(saved.getName()).isEqualTo("既存ニット");
        assertThat(saved.getStock()).isEqualTo(10);
        assertThat(saved.getVersion()).isEqualTo(existing.getVersion());
    }

    @Test
    void uploadPreviewAccessAndExplicitSaveKeepOriginalUntilConfirmation() throws Exception {
        String path = upload(png());
        unchanged();
        assertThat(images.forSave(path)).isEqualTo(path);
        mvc.perform(get(path)).andExpect(status().isOk()).andExpect(content().contentType("image/png"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
        mvc.perform(get("/admin/products/" + existing.getId() + "/edit").with(user("manager").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("この画像を使用する")));
        mvc.perform(post("/admin/products/save").with(user("manager").roles("ADMIN")).with(csrf())
                .param("id", existing.getId().toString()).param("version", existing.getVersion().toString())
                .param("name", existing.getName()).param("price", "2400").param("stock", "10")
                .param("category", "レディース").param("active", "true").param("imageUrl", path))
                .andExpect(redirectedUrl("/admin/products"));
        assertThat(products.findById(existing.getId()).orElseThrow().getImageUrl()).isEqualTo(path);
        assertThat(Files.readAllBytes(Path.of("src/main/resources/static/images/products/knit1.png"))).isNotEmpty();
        // サービス再生成後も、同じ保存先を指定すれば画像を参照できる。
        assertThat(new ProductImageStorage(directory.toString(), 2000).find(path)).isPresent();
    }

    @Test
    void jpgAndWebpAreDecodedAndUniqueNamesNeverOverwrite() throws Exception {
        String first = upload(png()), second = upload(png());
        assertThat(first).isNotEqualTo(second);
        mvc.perform(get(first)).andExpect(status().isOk());
        String jpeg = upload(new MockMultipartFile("file", "test.JPG", "image/jpeg", bitmap("jpg")));
        assertThat(jpeg).endsWith(".jpg");
        mvc.perform(get(jpeg)).andExpect(content().contentType("image/jpeg"));
        byte[] webp = Base64.getDecoder().decode("UklGRiIAAABXRUJQVlA4IBYAAAAwAQCdASoBAAEADsD+JaQAA3AAAAAA");
        String normalized = upload(new MockMultipartFile("file", "test.webp", "image/webp", webp));
        assertThat(normalized).endsWith(".png");
        byte[] data = mvc.perform(get(normalized)).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(ImageIO.read(new ByteArrayInputStream(data))).isNotNull();
        unchanged();
    }

    @Test
    void invalidContentMismatchOversizeAndMultipleFilesAreRejectedWithoutChanges() throws Exception {
        long beforeFiles;
        try (var files = Files.list(directory)) { beforeFiles = files.count(); }
        List<MockMultipartFile> invalid = List.of(
                new MockMultipartFile("file", "fake.png", "image/png", "<script>alert(1)</script>".getBytes()),
                new MockMultipartFile("file", "fake.svg", "image/svg+xml", "<svg/>".getBytes()),
                new MockMultipartFile("file", "wrong.jpg", "image/jpeg", bitmap("png")),
                new MockMultipartFile("file", "wrong.png", "image/jpeg", bitmap("png")),
                new MockMultipartFile("file", "empty.png", "image/png", new byte[0]),
                new MockMultipartFile("file", "large.png", "image/png", new byte[(int) ProductImageStorage.MAX_BYTES + 1]),
                new MockMultipartFile("file", "broken.png", "image/png", Arrays.copyOf(bitmap("png"), 35)));
        for (var file : invalid) {
            mvc.perform(multipart("/admin/product-images").file(file).with(user("manager").roles("ADMIN")).with(csrf()))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(multipart("/admin/product-images").file(png()).file(png())
                .with(user("manager").roles("ADMIN")).with(csrf())).andExpect(status().isBadRequest());
        mvc.perform(multipart("/admin/product-images").file(new MockMultipartFile("other", "x.png", "image/png", bitmap("png")))
                .with(user("manager").roles("ADMIN")).with(csrf())).andExpect(status().isBadRequest());
        try (var files = Files.list(directory)) { assertThat(files.count()).isEqualTo(beforeFiles); }
        unchanged();
    }

    @Test
    void oversizedDimensionsAndInjectedTrailingDataAreHandledSafely() throws Exception {
        ByteArrayOutputStream oversized = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(6001, 1, BufferedImage.TYPE_INT_RGB), "png", oversized);
        mvc.perform(multipart("/admin/product-images")
                .file(new MockMultipartFile("file", "wide.png", "image/png", oversized.toByteArray()))
                .with(user("manager").roles("ADMIN")).with(csrf())).andExpect(status().isBadRequest());
        ByteArrayOutputStream withPayload = new ByteArrayOutputStream();
        withPayload.write(bitmap("png"));
        withPayload.write("<script>untrusted-payload</script>".getBytes());
        String path = upload(new MockMultipartFile("file", "../../original.png", "image/png", withPayload.toByteArray()));
        byte[] normalized = mvc.perform(get(path)).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(normalized, java.nio.charset.StandardCharsets.ISO_8859_1)).doesNotContain("untrusted-payload");
        unchanged();
    }

    @Test
    void uploadIoFailureReturnsSafeErrorWithoutChangingProduct() throws Exception {
        MockMultipartFile unreadable = new MockMultipartFile("file", "io.png", "image/png", bitmap("png")) {
            @Override
            public InputStream getInputStream() throws IOException { throw new IOException("test read failure"); }
        };
        mvc.perform(multipart("/admin/product-images").file(unreadable)
                .with(user("manager").roles("ADMIN")).with(csrf()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("商品情報は変更されていません")));
        unchanged();
    }

    @Test
    void permissionCsrfAndMissingImageAccessUseExistingSecurityRules() throws Exception {
        mvc.perform(multipart("/admin/product-images").file(png()).with(csrf())).andExpect(status().is3xxRedirection());
        mvc.perform(multipart("/admin/product-images").file(png()).with(user("customer")).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(multipart("/admin/product-images").file(png()).with(user("manager").roles("ADMIN")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/images/uploads/00000000000000000000000000000000.png")).andExpect(status().isNotFound());
        mvc.perform(get("/images/uploads/not-an-image.txt")).andExpect(status().isNotFound());
        assertThat(images.resolve("/images/uploads/../application.properties")).isEqualTo(ProductImageService.PLACEHOLDER);
        unchanged();
    }

    @Test
    void sessionAndDiskQuotasAreEnforced() throws Exception {
        MockHttpSession session = new MockHttpSession();
        for (int count = 0; count < 20; count++) {
            mvc.perform(multipart("/admin/product-images").file(png()).session(session)
                    .with(user("manager").roles("ADMIN")).with(csrf())).andExpect(status().isCreated());
        }
        mvc.perform(multipart("/admin/product-images").file(png()).session(session)
                .with(user("manager").roles("ADMIN")).with(csrf())).andExpect(status().isBadRequest());
        Path limited = Files.createTempDirectory(directory, "quota-");
        ProductImageStorage storage = new ProductImageStorage(limited.toString(), 1);
        String path = storage.store(png()).imageUrl();
        assertThatThrownBy(() -> storage.store(png())).isInstanceOf(IllegalArgumentException.class);
        assertThat(storage.find(path)).isPresent();
        unchanged();
    }

    @Test
    void failedSaveAfterUploadDoesNotChangeProductOrRemoveEitherImage() throws Exception {
        String path = upload(png());
        mvc.perform(post("/admin/products/save").with(user("manager").roles("ADMIN")).with(csrf())
                .param("id", existing.getId().toString()).param("version", existing.getVersion().toString())
                .param("name", "変更しない").param("price", "-1").param("stock", "10")
                .param("category", "レディース").param("active", "true").param("imageUrl", path))
                .andExpect(status().isOk());
        unchanged();
        mvc.perform(get(path)).andExpect(status().isOk());
        mvc.perform(get("/images/products/knit1.png")).andExpect(status().isOk());
        Path blocked = Files.createTempFile(directory, "not-directory-", ".txt");
        assertThatThrownBy(() -> new ProductImageStorage(blocked.toString(), 10).store(png()))
                .isInstanceOf(IOException.class);
        unchanged();
    }
}
