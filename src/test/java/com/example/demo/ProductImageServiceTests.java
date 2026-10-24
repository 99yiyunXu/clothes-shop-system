package com.example.demo;

import com.example.demo.service.ProductImageService;
import com.example.demo.service.ProductImageStorage;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import static org.assertj.core.api.Assertions.*;

/** データベースに接続せず、既存画像の互換性と代替画像の選択を確認する。 */
class ProductImageServiceTests {
    private final ProductImageService images = new ProductImageService(new DefaultResourceLoader(),
            new ProductImageStorage("target/image-unit-storage", 10));

    @Test
    void existingImagesAndPlaceholderRemainAvailable() {
        assertThat(images.resolve("/images/products/knit1.png")).isEqualTo("/images/products/knit1.png");
        assertThat(images.exists(ProductImageService.PLACEHOLDER)).isTrue();
        assertThat(images.forSave(ProductImageService.PLACEHOLDER)).isEqualTo(ProductImageService.PLACEHOLDER);
    }

    @Test
    void missingAndUnsafePathsUsePlaceholderOnlyWhenDisplayed() {
        for (String path : new String[]{null, "", " ", "/images/products/missing.png",
                "/images/../application.properties", "https://example.com/image.png", "/images/common/other.svg"}) {
            assertThat(images.resolve(path)).isEqualTo(ProductImageService.PLACEHOLDER);
        }
        assertThat(images.forSave(null)).isEqualTo(ProductImageService.PLACEHOLDER);
        assertThat(images.forSave(" ")).isEqualTo(ProductImageService.PLACEHOLDER);
        assertThatThrownBy(() -> images.forSave("/images/products/missing.png"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> images.forSave("/images/../application.properties"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
