package com.example.demo;

import com.example.demo.entity.Product;
import com.example.demo.repository.ProductRepository;
import com.example.demo.service.AdminProductService;
import com.example.demo.service.ProductImageService;
import com.example.demo.service.ProuductService;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 商品管理の検証には専用の H2 メモリ DB を使用し、実データを変更しない。 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:luna_catalog_test;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminCatalogTests {
    @Autowired MockMvc mvc;
    @Autowired ProductRepository products;
    @Autowired AdminProductService admin;
    @Autowired ProuductService catalog;

    @BeforeEach
    void prepare() { products.deleteAll(); }

    private MockHttpServletRequestBuilder form(String name, boolean active) {
        return form(name, active, 2400);
    }

    private MockHttpServletRequestBuilder form(String name, boolean active, int price) {
        var request = post("/admin/products/save").with(user("manager").roles("ADMIN")).with(csrf())
                .param("name", name).param("price", Integer.toString(price)).param("stock", "0")
                .param("category", "レディース").param("imageUrl", "")
                .param("_active", "on").param("_sale", "on");
        if (active) request.param("active", "true");
        return request;
    }

    @Test
    void newPublishedProductWithoutImageImmediatelyEntersCatalogAndHome() throws Exception {
        mvc.perform(form("新着ニット", true)).andExpect(redirectedUrl("/admin/products"))
                .andExpect(flash().attribute("message", containsString("商品を追加しました。公開中")));
        Product created = products.findAll().getFirst();
        assertThat(created.getImageUrl()).isEqualTo(ProductImageService.PLACEHOLDER);
        assertThat(created.getStock()).isZero();
        assertThat(catalog.searchPage("新着", "レディース", false, 0).getContent())
                .extracting(Product::getId).containsExactly(created.getId());
        // 候補を1件に限定して、新規商品が抽選対象になることを確実に確認する。
        assertThat(catalog.recommended()).extracting(Product::getId).containsExactly(created.getId());
        mvc.perform(get("/products").with(user("customer")).param("keyword", "新着").param("category", "レディース"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("新着ニット")))
                .andExpect(content().string(containsString("src=\"/images/common/no-image.svg\"")));
        var home = mvc.perform(get("/home").with(user("customer"))).andExpect(status().isOk()).andReturn();
        assertThat(home.getModelAndView()).isNotNull();
        @SuppressWarnings("unchecked")
        List<Product> recommended = (List<Product>) home.getModelAndView().getModel().get("recommendedProducts");
        assertThat(recommended).extracting(Product::getId).containsExactly(created.getId());
    }

    @Test
    void draftEditPublishAndDeactivatePreserveProductAndExplainVisibility() throws Exception {
        mvc.perform(get("/admin/products/new").with(user("manager").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("商品を登録する")))
                .andExpect(content().string(containsString("公開する（販売中）")));
        mvc.perform(form("非公開ニット", false)).andExpect(redirectedUrl("/admin/products"))
                .andExpect(flash().attribute("message", containsString("非公開で保存しました")));
        Product draft = products.findAll().getFirst();
        assertThat(draft.isActive()).isFalse();
        assertThat(catalog.searchPage("", "", false, 0).getTotalElements()).isZero();
        assertThat(catalog.recommended()).isEmpty();
        mvc.perform(get("/admin/products").with(user("manager").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("登録商品：1件")))
                .andExpect(content().string(containsString("編集・再公開")));
        mvc.perform(get("/admin/products/" + draft.getId() + "/edit").with(user("manager").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("変更を保存する")))
                .andExpect(content().string(containsString("現在の在庫を置き換えます")));
        mvc.perform(form("公開ニット", true, 3500).param("id", draft.getId().toString())
                .param("version", draft.getVersion().toString()))
                .andExpect(redirectedUrl("/admin/products"))
                .andExpect(flash().attribute("message", containsString("商品を更新しました。公開中")));
        Product published = products.findById(draft.getId()).orElseThrow();
        assertThat(published.isActive()).isTrue();
        assertThat(published.getName()).isEqualTo("公開ニット");
        assertThat(published.getPrice()).isEqualTo(3500);
        assertThat(catalog.searchPage("公開ニット", "", false, 0).getTotalElements()).isEqualTo(1);
        assertThat(catalog.recommended()).extracting(Product::getId).containsExactly(draft.getId());
        mvc.perform(post("/admin/products/" + draft.getId() + "/delete")
                .with(user("manager").roles("ADMIN")).with(csrf()))
                .andExpect(redirectedUrl("/admin/products"))
                .andExpect(flash().attribute("message", containsString("商品データと注文履歴は保持")));
        assertThat(products.count()).isEqualTo(1);
        assertThat(products.findById(draft.getId()).orElseThrow().isActive()).isFalse();
        assertThat(catalog.searchPage("", "", false, 0).getTotalElements()).isZero();
        assertThat(catalog.recommended()).isEmpty();
    }

    @Test
    void productNameMatchesExistingMysqlColumnLimitWithoutSchemaChanges() throws Exception {
        mvc.perform(form("あ".repeat(100), true)).andExpect(redirectedUrl("/admin/products"));
        mvc.perform(form("あ".repeat(101), true)).andExpect(status().isOk())
                .andExpect(content().string(containsString("商品名を1～100文字")));
        assertThat(products.count()).isEqualTo(1);
    }

    @Test
    void seventyPublishedProductsRemainReachableAcrossPagesAndRecommendationIsLimitedToFour() throws Exception {
        Set<Integer> expected = new HashSet<>();
        for (int index = 0; index < 70; index++) {
            Product product = new Product();
            product.setName("検証ニット" + index); product.setPrice(2400); product.setStock(0);
            product.setCategory("レディース"); product.setImageUrl(""); product.setSale(true);
            admin.save(product);
        }
        products.findAll().forEach(product -> expected.add(product.getId()));
        Set<Integer> found = new HashSet<>();
        for (int page = 0; page < 6; page++) {
            var result = catalog.searchPage("検証ニット", "レディース", true, page);
            assertThat(result.getTotalElements()).isEqualTo(70);
            assertThat(result.getTotalPages()).isEqualTo(6);
            assertThat(result.getNumberOfElements()).isEqualTo(page == 5 ? 10 : 12);
            result.forEach(product -> assertThat(found.add(product.getId())).isTrue());
        }
        assertThat(found).isEqualTo(expected);
        assertThat(catalog.recommended()).hasSize(4)
                .extracting(Product::getId).doesNotHaveDuplicates().allMatch(expected::contains);
        mvc.perform(get("/products").with(user("customer")).param("keyword", "検証ニット")
                .param("category", "レディース").param("sale", "true").param("page", "1"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("検索結果：70件")))
                .andExpect(content().string(containsString("2 / 6 ページ")))
                .andExpect(content().string(containsString("sale=true")))
                .andExpect(content().string(containsString("page=2")));
        assertThat(admin.list(5).getNumberOfElements()).isEqualTo(10);
    }
}
