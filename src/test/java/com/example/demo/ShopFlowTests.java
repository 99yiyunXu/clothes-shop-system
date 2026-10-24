package com.example.demo;

import com.example.demo.entity.*;
import com.example.demo.entity.ShopOrder.Status;
import com.example.demo.repository.*;
import com.example.demo.service.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ShopFlowTests {
    @Autowired MockMvc mvc;
    @Autowired UserService users;
    @Autowired CartService carts;
    @Autowired FavoriteService favorites;
    @Autowired OrderService orders;
    @Autowired ProuductService catalog;
    @Autowired AdminProductService admin;
    @Autowired ProductRepository productRepository;
    @Autowired UserRepository userRepository;
    @Autowired CartRepository cartRepository;
    @Autowired FavoriteRepository favoriteRepository;
    @Autowired OrderRepository orderRepository;
    @Autowired PasswordEncoder encoder;
    Product product;

    @BeforeEach
    void prepare() {
        orderRepository.deleteAll(); cartRepository.deleteAll(); favoriteRepository.deleteAll();
        productRepository.deleteAll(); userRepository.deleteAll();
        users.register("alice", "password123", "password123");
        users.register("bob", "password123", "password123");
        users.register("manager", "password123", "password123");
        ShopUser manager = users.require("manager"); manager.setRole("ADMIN"); userRepository.save(manager);
        product = product("ニット", 4900, 10);
    }

    private Product product(String name, int price, int stock) {
        Product item = new Product(); item.setName(name); item.setPrice(price); item.setStock(stock);
        item.setCategory("レディース"); item.setImageUrl("/images/products/knit1.png");
        return productRepository.saveAndFlush(item);
    }
    private Long checkout() {
        carts.add("alice", product.getId(), 2);
        return orders.checkout("alice", UUID.randomUUID().toString(), "テスト利用者", "東京都千代田区 100-0001");
    }

    @Test
    void registrationHashesPasswordsAndCannotGrantAdmin() throws Exception {
        mvc.perform(post("/register").with(csrf()).param("userName", "New_User")
                .param("password", "password123").param("confirmation", "password123").param("role", "ADMIN"))
            .andExpect(status().is3xxRedirection());
        ShopUser registered = users.require("new_user");
        assertThat(registered.getRole()).isEqualTo("USER");
        assertThat(registered.getPasswordHash()).isNotEqualTo("password123");
        assertThat(encoder.matches("password123", registered.getPasswordHash())).isTrue();
        assertThatThrownBy(() -> users.register("alice", "password123", "password123"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> users.register("bad", "short", "short"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> users.register("long_unicode", "あ".repeat(30), "あ".repeat(30)))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("長すぎます");
    }

    @Test
    void loginLogoutAndAccessControl() throws Exception {
        mvc.perform(get("/home")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/products")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/admin/products").with(user("alice"))).andExpect(status().isForbidden());
        mvc.perform(post("/login").with(csrf()).param("userName", "alice").param("password", "wrong"))
            .andExpect(redirectedUrl("/?error"));
        var result = mvc.perform(post("/login").with(csrf()).param("userName", "ALICE").param("password", "password123"))
            .andExpect(redirectedUrl("/home")).andReturn();
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        mvc.perform(get("/home").session(session)).andExpect(status().isOk());
        mvc.perform(post("/logout").session(session).with(csrf())).andExpect(redirectedUrl("/?logout"));
        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void homeAdminEntryIsVisibleOnlyToAdminAndPrecedesLogout() throws Exception {
        String adminHome = mvc.perform(get("/home").with(user("manager").roles("ADMIN")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(adminHome).contains("href=\"/admin\"", "class=\"admin-entry\"");
        int entry = adminHome.indexOf(">管理者画面</a>");
        assertThat(entry).isGreaterThanOrEqualTo(0);
        // ログアウトの直前に入口を置き、既存のナビゲーション項目は維持する。
        assertThat(adminHome.substring(entry, adminHome.indexOf("ログアウト", entry)))
                .doesNotContain("<a ");
        mvc.perform(get("/home").with(user("alice"))).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString(">管理者画面</a>"))));
        mvc.perform(get("/")).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString(">管理者画面</a>"))));
        mvc.perform(get("/home")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/luna/home").contextPath("/luna").with(user("manager").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("href=\"/luna/admin\"")));
    }

    @Test
    void adminCanSwitchBetweenShopAndAllExistingAdminPagesWithSameSession() throws Exception {
        Long orderId = checkout();
        var login = mvc.perform(post("/login").with(csrf()).param("userName", "manager").param("password", "password123"))
                .andExpect(redirectedUrl("/home")).andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        for (String path : List.of("/admin", "/admin/products", "/admin/products/new",
                "/admin/products/" + product.getId() + "/edit", "/admin/orders", "/admin/orders/" + orderId)) {
            mvc.perform(get(path).session(session)).andExpect(status().isOk())
                    .andExpect(model().attribute("isAdminPage", true))
                    .andExpect(content().string(containsString("admin-header")))
                    .andExpect(content().string(containsString("href=\"/home\">ショップに戻る</a>")));
        }
        mvc.perform(get("/home").session(session)).andExpect(status().isOk())
                .andExpect(content().string(containsString(">管理者画面</a>")));
        mvc.perform(get("/products/" + product.getId()).session(session)).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("ショップに戻る"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("admin-header"))));
        mvc.perform(get("/luna/admin").contextPath("/luna").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("href=\"/luna/home\">ショップに戻る</a>")));
    }

    @Test
    void hiddenAdminEntryDoesNotReplaceServerSideAccessControl() throws Exception {
        for (String path : List.of("/admin", "/admin/products", "/admin/products/new",
                "/admin/products/" + product.getId() + "/edit", "/admin/orders", "/admin/orders/1")) {
            mvc.perform(get(path).with(user("alice"))).andExpect(status().isForbidden());
            mvc.perform(get(path)).andExpect(status().is3xxRedirection());
        }
        mvc.perform(post("/admin/products/" + product.getId() + "/delete").with(user("alice")).with(csrf()))
                .andExpect(status().isForbidden());
        assertThat(productRepository.findById(product.getId()).orElseThrow().isActive()).isTrue();
    }

    @Test
    void writesRequireCsrf() throws Exception {
        mvc.perform(post("/cart/add").with(user("alice")).param("productId", product.getId().toString()))
            .andExpect(status().isForbidden());
        mvc.perform(post("/register").param("userName", "missing_token").param("password", "password123")
                .param("confirmation", "password123")).andExpect(status().isForbidden());
    }

    @Test
    void pagesRenderWithDatabaseDataAndWithoutExternalLoginScript() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk()).andExpect(content().string(containsString("name=\"_csrf\"")))
            .andExpect(content().string(org.hamcrest.Matchers.not(containsString("jquery"))));
        mvc.perform(get("/register")).andExpect(status().isOk());
        mvc.perform(get("/home").with(user("alice"))).andExpect(status().isOk())
            .andExpect(content().string(containsString("新しい季節の装い")))
            .andExpect(content().string(containsString("/products/" + product.getId())));
        mvc.perform(get("/products").with(user("alice"))).andExpect(status().isOk());
        mvc.perform(get("/products/" + product.getId()).with(user("alice"))).andExpect(status().isOk());
        mvc.perform(get("/success").with(user("alice"))).andExpect(status().isOk());
        mvc.perform(get("/api/random-image").with(user("alice"))).andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.matchesPattern("/images/woman[1-4]\\.png")));
    }

    @Test
    void missingImagesRenderAcrossPagesWithoutChangingStoredPaths() throws Exception {
        String missing = "/images/products/legacy-missing.png";
        product.setImageUrl(missing);
        productRepository.saveAndFlush(product);
        favorites.add("alice", product.getId());
        carts.add("alice", product.getId(), 1);
        for (String path : List.of("/home", "/products", "/products/" + product.getId(), "/favorites", "/cart")) {
            mvc.perform(get(path).with(user("alice"))).andExpect(status().isOk())
                    .andExpect(content().string(containsString("data-product-image")))
                    .andExpect(content().string(containsString("src=\"/images/common/no-image.svg\"")));
        }
        Long id = orders.checkout("alice", UUID.randomUUID().toString(), "利用者", "東京都");
        mvc.perform(get("/orders/" + id).with(user("alice"))).andExpect(status().isOk())
                .andExpect(content().string(containsString("src=\"/images/common/no-image.svg\"")));
        mvc.perform(get("/admin/products").with(user("manager").roles("ADMIN"))).andExpect(status().isOk())
                .andExpect(content().string(containsString("src=\"/images/common/no-image.svg\"")));
        assertThat(productRepository.findById(product.getId()).orElseThrow().getImageUrl()).isEqualTo(missing);
        assertThat(orders.detail("alice", id).getItems().getFirst().getImageUrl()).isEqualTo(missing);
        mvc.perform(get("/images/common/no-image.svg")).andExpect(status().isOk());
        mvc.perform(get("/js/product-images.js")).andExpect(status().isOk());
        mvc.perform(get("/luna/products").contextPath("/luna").with(user("alice")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("src=\"/luna/images/common/no-image.svg\"")))
                .andExpect(content().string(containsString("data-fallback=\"/luna/images/common/no-image.svg\"")));
    }

    @Test
    void adminCanSaveProductWithoutImage() throws Exception {
        mvc.perform(post("/admin/products/save").with(user("manager").roles("ADMIN")).with(csrf())
                .param("name", "画像準備中の商品").param("price", "1200").param("stock", "0")
                .param("category", "レディース").param("active", "true").param("imageUrl", ""))
                .andExpect(redirectedUrl("/admin/products"));
        assertThat(productRepository.findByNameContainingIgnoreCaseOrderByIdDesc("画像準備中の商品"))
                .singleElement().extracting(Product::getImageUrl).isEqualTo(ProductImageService.PLACEHOLDER);
    }

    @Test
    void adminPublicationFlowsThroughSearchRecommendationAndPurchase() throws Exception {
        mvc.perform(post("/admin/products/save").with(user("manager").roles("ADMIN")).with(csrf())
                .param("name", "ブラウンケーブルニット").param("price", "3000").param("stock", "3")
                .param("category", "レディース").param("imageUrl", "/images/products/knit2.png")
                .param("active", "true"))
                .andExpect(redirectedUrl("/admin/products"));
        Product published = productRepository.findByNameContainingIgnoreCaseOrderByIdDesc("ブラウンケーブルニット").getFirst();
        mvc.perform(get("/products").with(user("alice")).param("keyword", published.getName()))
                .andExpect(status().isOk()).andExpect(content().string(containsString("/products/" + published.getId())));
        mvc.perform(get("/products/" + published.getId()).with(user("alice")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("カートに追加")));
        // 候補が4件以下のため、新規商品がホームの抽選対象に入ることを確実に確認できる。
        assertThat(catalog.recommended()).extracting(Product::getId).contains(published.getId());
        mvc.perform(post("/cart/add").with(user("alice")).with(csrf())
                .param("productId", published.getId().toString()).param("quantity", "2"))
                .andExpect(redirectedUrl("/cart"));
        MockHttpSession session = new MockHttpSession();
        mvc.perform(get("/checkout").with(user("alice")).session(session)).andExpect(status().isOk());
        var result = mvc.perform(post("/orders").with(user("alice")).with(csrf()).session(session)
                .param("checkoutToken", (String) session.getAttribute("checkoutToken"))
                .param("recipient", "検証利用者").param("address", "東京都千代田区"))
                .andExpect(status().is3xxRedirection()).andReturn();
        String location = result.getResponse().getRedirectedUrl();
        assertThat(location).startsWith("/orders/");
        Long orderId = Long.valueOf(location.substring("/orders/".length()));
        assertThat(orders.detail("alice", orderId).getTotal()).isEqualTo(6500);
        assertThat(productRepository.findById(published.getId()).orElseThrow().getStock()).isEqualTo(1);
        mvc.perform(post("/orders/" + orderId + "/pay-demo").with(user("alice")).with(csrf()))
                .andExpect(redirectedUrl("/orders/" + orderId));
        assertThat(orders.detail("alice", orderId).getStatus()).isEqualTo(Status.PAID);
    }

    @Test
    void searchCombinesFiltersAndTreatsWildcardsLiterally() {
        Product sale = product("Sale Knit", 1200, 4); sale.setSale(true); productRepository.save(sale);
        product("100% cotton", 1000, 2);
        Product inactive = product("Hidden Knit", 1000, 2); inactive.setActive(false); productRepository.save(inactive);
        assertThat(catalog.searchPage("knit", "レディース", true, 0).getTotalElements()).isEqualTo(1);
        assertThat(catalog.searchPage("%", "", false, 0).getTotalElements()).isEqualTo(1);
        assertThat(catalog.searchPage("missing", "", false, 0).isEmpty()).isTrue();
        assertThat(catalog.searchPage("", "", false, -1).getNumber()).isZero();
    }

    @Test
    void paginationAndNewArrivalsUseCreatedTime() throws Exception {
        // 登録日時と商品番号の順序が異なる場合も新着順で表示する
        Product older = new Product(); older.setName("過去の服"); older.setPrice(1000); older.setStock(2);
        older.setCategory("レディース"); older.setImageUrl("/images/products/knit1.png");
        older.setCreatedAt(LocalDateTime.now().minusDays(10)); productRepository.saveAndFlush(older);
        assertThat(catalog.newest().getFirst().getId()).isEqualTo(product.getId());
        for (int i = 0; i < 15; i++) product("商品" + i, 1000, 3);
        var first = catalog.searchPage("", "", false, 0);
        assertThat(first.getContent()).hasSize(12);
        assertThat(first.hasNext()).isTrue();
        assertThat(catalog.searchPage("", "", false, 1).getContent()).hasSize(5);
        assertThat(catalog.findProductList()).hasSize(17);
        assertThat(catalog.searchProducts("商品", "レディース")).hasSize(15);
        assertThat(catalog.newest()).hasSize(4);
        mvc.perform(get("/products").with(user("alice")).param("page", "1"))
            .andExpect(status().isOk()).andExpect(content().string(containsString("前へ")));
    }

    @Test
    void cartCanAddUpdateRemoveAndIsPrivate() throws Exception {
        carts.add("alice", product.getId(), 1); carts.add("alice", product.getId(), 1);
        var cart = carts.summary("alice");
        assertThat(cart.lines()).hasSize(1); assertThat(cart.subtotal()).isEqualTo(9800);
        assertThat(cart.shippingFee()).isEqualTo(500);
        Long lineId = cart.lines().getFirst().id();
        assertThat(carts.summary("bob").lines()).isEmpty();
        assertThatThrownBy(() -> carts.remove("bob", lineId)).isInstanceOf(ResponseStatusException.class);
        carts.update("alice", lineId, 3);
        assertThat(carts.summary("alice").shippingFee()).isZero();
        mvc.perform(get("/cart").with(user("alice"))).andExpect(status().isOk());
        carts.remove("alice", lineId); assertThat(carts.summary("alice").lines()).isEmpty();
    }

    @Test
    void invalidQuantityAndStockAreRejected() {
        assertThatThrownBy(() -> carts.add("alice", product.getId(), 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> carts.add("alice", product.getId(), 99)).isInstanceOf(IllegalArgumentException.class);
        carts.add("alice", product.getId(), 1);
        assertThatThrownBy(() -> carts.update("alice", carts.summary("alice").lines().getFirst().id(), -1))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void favoritesAreIdempotentAndPrivate() throws Exception {
        favorites.add("alice", product.getId()); favorites.add("alice", product.getId());
        assertThat(favorites.list("alice", 0).getTotalElements()).isEqualTo(1);
        assertThat(favorites.contains("bob", product.getId())).isFalse();
        mvc.perform(get("/favorites").with(user("alice"))).andExpect(status().isOk());
        favorites.remove("bob", product.getId()); assertThat(favorites.contains("alice", product.getId())).isTrue();
        favorites.remove("alice", product.getId()); assertThat(favorites.contains("alice", product.getId())).isFalse();
    }

    @Test
    void checkoutUsesCurrentPricesAndPersistsSnapshots() throws Exception {
        carts.add("alice", product.getId(), 2);
        product.setPrice(6000); productRepository.saveAndFlush(product);
        Long id = orders.checkout("alice", UUID.randomUUID().toString(), "利用者", "東京都");
        var order = orders.detail("alice", id);
        assertThat(order.getSubtotal()).isEqualTo(12000); assertThat(order.getShippingFee()).isZero();
        assertThat(order.getItems().getFirst().getUnitPrice()).isEqualTo(6000);
        assertThat(productRepository.findById(product.getId()).orElseThrow().getStock()).isEqualTo(8);
        assertThat(carts.summary("alice").lines()).isEmpty();
        product = productRepository.findById(product.getId()).orElseThrow();
        product.setPrice(7000); product.setName("変更後"); productRepository.saveAndFlush(product);
        assertThat(orders.detail("alice", id).getItems().getFirst().getProductName()).isEqualTo("ニット");
        mvc.perform(get("/orders").with(user("alice"))).andExpect(status().isOk());
        mvc.perform(get("/orders/" + id).with(user("alice"))).andExpect(status().isOk());
    }

    @Test
    void checkoutDuplicateTokenDoesNotChargeStockTwice() {
        carts.add("alice", product.getId(), 2);
        String token = UUID.randomUUID().toString();
        Long first = orders.checkout("alice", token, "利用者", "東京都");
        assertThat(orders.checkout("alice", token, "利用者", "東京都")).isEqualTo(first);
        assertThat(orderRepository.count()).isEqualTo(1);
        assertThat(productRepository.findById(product.getId()).orElseThrow().getStock()).isEqualTo(8);
    }

    @Test
    void checkoutFailureRollsBackAllInventoryAndKeepsCart() {
        Product other = product("コート", 1000, 1);
        carts.add("alice", product.getId(), 2); carts.add("alice", other.getId(), 1);
        other.setStock(0); productRepository.saveAndFlush(other);
        assertThatThrownBy(() -> orders.checkout("alice", UUID.randomUUID().toString(), "利用者", "東京都"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThat(productRepository.findById(product.getId()).orElseThrow().getStock()).isEqualTo(10);
        assertThat(orderRepository.count()).isZero(); assertThat(carts.summary("alice").lines()).hasSize(2);
    }

    @Test
    void cancelRestoresStockOnceAndCannotCancelPaidOrder() {
        Long id = checkout(); orders.cancel("alice", id); orders.cancel("alice", id);
        assertThat(productRepository.findById(product.getId()).orElseThrow().getStock()).isEqualTo(10);
        Long paid = checkout(); orders.payDemo("alice", paid);
        assertThatThrownBy(() -> orders.cancel("alice", paid)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void orderAccessAndActionsArePrivate() throws Exception {
        Long id = checkout();
        mvc.perform(get("/orders/" + id).with(user("bob"))).andExpect(status().isNotFound());
        assertThatThrownBy(() -> orders.payDemo("bob", id)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> orders.cancel("bob", id)).isInstanceOf(ResponseStatusException.class);
        assertThat(orders.detail("alice", id).getStatus()).isEqualTo(Status.PENDING);
    }

    @Test
    void paymentShippingReceiptAndReturnFollowAllowedTransitions() throws Exception {
        Long id = checkout();
        assertThatThrownBy(() -> orders.ship(id, "TRACK1")).isInstanceOf(IllegalArgumentException.class);
        orders.payDemo("alice", id); orders.payDemo("alice", id);
        orders.ship(id, "TRACK1"); orders.receive("alice", id);
        orders.requestReturn("alice", id, "サイズが合いません。"); orders.approveReturn(id); orders.approveReturn(id);
        assertThat(orders.detail("alice", id).getStatus()).isEqualTo(Status.RETURNED);
        assertThat(productRepository.findById(product.getId()).orElseThrow().getStock()).isEqualTo(10);
        mvc.perform(get("/admin/orders").with(user("manager").roles("ADMIN"))).andExpect(status().isOk());
        mvc.perform(get("/admin/orders/" + id).with(user("manager").roles("ADMIN"))).andExpect(status().isOk());
    }

    @Test
    void adminCanManageProductsWithoutDeletingHistory() throws Exception {
        Long id = checkout();
        mvc.perform(get("/admin/products").with(user("manager").roles("ADMIN"))).andExpect(status().isOk());
        mvc.perform(get("/admin/products/new").with(user("manager").roles("ADMIN"))).andExpect(status().isOk());
        mvc.perform(get("/admin/products/" + product.getId() + "/edit").with(user("manager").roles("ADMIN")))
            .andExpect(status().isOk());
        mvc.perform(post("/admin/products/save").with(user("manager").roles("ADMIN")).with(csrf())
                .param("name", "新商品").param("price", "1234").param("stock", "5").param("category", "メンズ")
                .param("imageUrl", "/images/products/coat1.png").param("active", "true"))
            .andExpect(redirectedUrl("/admin/products"));
        admin.deactivate(product.getId());
        assertThatThrownBy(() -> catalog.detail(product.getId())).isInstanceOf(ResponseStatusException.class);
        assertThat(orders.detail("alice", id).getItems()).hasSize(1);
        orders.cancel("alice", id);
        assertThat(productRepository.findById(product.getId()).orElseThrow().getStock()).isEqualTo(10);
    }

    @Test
    void checkoutSummaryRendersImagesAmountsAndOneCompleteOrderForm() throws Exception {
        Product missing = product("画像準備中のニット", 1000, 5);
        missing.setImageUrl("/images/products/missing.png"); productRepository.saveAndFlush(missing);
        carts.add("alice", product.getId(), 2); carts.add("alice", missing.getId(), 1);
        var response = mvc.perform(get("/checkout").with(user("alice")))
                .andExpect(status().isOk()).andReturn().getResponse();
        String html = response.getContentAsString();
        assertThat(html).contains("/images/products/knit1.png", "/images/common/no-image.svg",
                "単価 ¥4,900", "数量 2", "小計 ¥9,800", "¥10,800", "送料無料", "aria-current=\"step\"");
        String form = checkoutForm(html);
        assertThat(form).contains("name=\"recipient\"", "name=\"address\"", "name=\"checkoutToken\"", "name=\"_csrf\"",
                "checkout-delivery", "checkout-summary", "注文を確定する");
        assertThat(form.indexOf("checkout-total\"")).isLessThan(form.indexOf("注文を確定する"));
        assertThat(form.indexOf("注文を確定する")).isLessThan(form.indexOf("</form>"));
        assertThat(form.substring(form.indexOf('>') + 1)).doesNotContain("<form");
        assertThat(html).doesNotContain(">管理者画面</a>");
        // レイアウト確認用の出力はビルド成果物に限定し、実データは使用しない。
        java.nio.file.Files.writeString(java.nio.file.Path.of("target/checkout-review.html"), html);
    }

    @Test
    void checkoutAdminNavigationHasOneEntryAndUsesContextAwareLinks() throws Exception {
        carts.add("manager", product.getId(), 1);
        String html = mvc.perform(get("/checkout").with(user("manager").roles("ADMIN")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(html.split(">管理者画面</a>", -1)).hasSize(2);
        assertThat(html).doesNotContain(">商品管理</a>", ">注文管理</a>");
        assertThat(html).contains("href=\"/admin\"", "href=\"/home\"", "class=\"checkout-header-menu\"");
        mvc.perform(get("/luna/checkout").contextPath("/luna").with(user("manager").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("action=\"/luna/orders\"")))
                .andExpect(content().string(containsString("href=\"/luna/admin\"")))
                .andExpect(content().string(containsString("src=\"/luna/images/products/knit1.png\"")));
    }

    @Test
    void summaryButtonSubmitsRenderedCsrfAddressAndTokenWithoutChangingCheckoutRules() throws Exception {
        carts.add("alice", product.getId(), 1);
        MockHttpSession session = new MockHttpSession();
        String html = mvc.perform(get("/checkout").session(session).with(user("alice")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String form = checkoutForm(html);
        var csrf = java.util.regex.Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(form);
        assertThat(csrf.find()).isTrue();
        String token = (String) session.getAttribute("checkoutToken");
        String address = "〒100-0001\n東京都千代田区 1-2-3\nLUNAマンション 101号室";
        mvc.perform(post("/orders").session(session).with(user("alice"))
                .param("checkoutToken", token).param("recipient", "山田 花子").param("address", address))
                .andExpect(status().isForbidden());
        assertThat(orderRepository.count()).isZero();
        assertThat(productRepository.findById(product.getId()).orElseThrow().getStock()).isEqualTo(10);
        var result = mvc.perform(post("/orders").session(session).with(user("alice"))
                .param("_csrf", csrf.group(1)).param("checkoutToken", token)
                .param("recipient", "山田 花子").param("address", address))
                .andExpect(status().is3xxRedirection()).andReturn();
        String location = result.getResponse().getRedirectedUrl();
        assertThat(location).startsWith("/orders/");
        var saved = orders.detail("alice", Long.valueOf(location.substring("/orders/".length())));
        assertThat(saved.getRecipient()).isEqualTo("山田 花子");
        assertThat(saved.getAddress()).isEqualTo(address);
        assertThat(saved.getSubtotal()).isEqualTo(4900);
        assertThat(saved.getShippingFee()).isEqualTo(500);
        assertThat(saved.getTotal()).isEqualTo(5400);
        assertThat(productRepository.findById(product.getId()).orElseThrow().getStock()).isEqualTo(9);
        assertThat(carts.summary("alice").lines()).isEmpty();
        assertThat(orderRepository.count()).isEqualTo(1);
        mvc.perform(post("/orders").session(session).with(user("alice")).with(csrf())
                .param("checkoutToken", token).param("recipient", "山田 花子").param("address", address))
                .andExpect(status().isBadRequest());
        assertThat(orderRepository.count()).isEqualTo(1);
    }

    @Test
    void checkoutValidationErrorKeepsEnteredAddressAndCartSummary() throws Exception {
        carts.add("alice", product.getId(), 1);
        MockHttpSession session = new MockHttpSession();
        mvc.perform(get("/checkout").session(session).with(user("alice"))).andExpect(status().isOk());
        String address = "〒100-0001 東京都千代田区 1-2-3";
        var invalid = mvc.perform(post("/orders").session(session).with(user("alice")).with(csrf())
                .param("checkoutToken", (String) session.getAttribute("checkoutToken"))
                .param("recipient", "").param("address", address))
                .andExpect(redirectedUrl("/checkout")).andReturn();
        mvc.perform(get("/checkout").session(session).with(user("alice")).flashAttrs(invalid.getFlashMap()))
                .andExpect(status().isOk()).andExpect(content().string(containsString(address)))
                .andExpect(content().string(containsString("お名前を1～100文字")))
                .andExpect(content().string(containsString("¥5,400")));
        assertThat(orderRepository.count()).isZero();
        assertThat(carts.summary("alice").lines()).hasSize(1);
        assertThat(productRepository.findById(product.getId()).orElseThrow().getStock()).isEqualTo(10);
    }

    private String checkoutForm(String html) {
        int start = html.indexOf("<form id=\"checkout-form\"");
        assertThat(start).isGreaterThanOrEqualTo(0);
        int end = html.indexOf("</form>", start);
        assertThat(end).isGreaterThan(start);
        return html.substring(start, end + "</form>".length());
    }

    @Test
    void checkoutPageAndPostWorkWithSessionToken() throws Exception {
        carts.add("alice", product.getId(), 1);
        MockHttpSession session = new MockHttpSession();
        mvc.perform(get("/checkout").session(session).with(user("alice"))).andExpect(status().isOk());
        String token = (String) session.getAttribute("checkoutToken");
        mvc.perform(post("/orders").session(session).with(user("alice")).with(csrf())
                .param("checkoutToken", token).param("recipient", "利用者").param("address", "東京都"))
            .andExpect(status().is3xxRedirection());
        mvc.perform(post("/orders").session(session).with(user("alice")).with(csrf())
                .param("checkoutToken", token).param("recipient", "利用者").param("address", "東京都"))
            .andExpect(status().isBadRequest());
        assertThat(orderRepository.count()).isEqualTo(1);
    }

    @Test
    void twoBuyersCannotPurchaseTheSameLastItem() throws Exception {
        product.setStock(1); productRepository.saveAndFlush(product);
        carts.add("alice", product.getId(), 1); carts.add("bob", product.getId(), 1);
        CountDownLatch ready = new CountDownLatch(2); CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            List<Future<Boolean>> results = new ArrayList<>();
            for (String username : List.of("alice", "bob")) results.add(pool.submit(() -> {
                ready.countDown(); if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException();
                try { orders.checkout(username, UUID.randomUUID().toString(), "利用者", "東京都"); return true; }
                catch (IllegalArgumentException ex) { return false; }
            }));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue(); start.countDown();
            int success = 0; for (Future<Boolean> result : results) if (result.get(20, TimeUnit.SECONDS)) success++;
            assertThat(success).isEqualTo(1);
        }
        assertThat(productRepository.findById(product.getId()).orElseThrow().getStock()).isZero();
        assertThat(orderRepository.count()).isEqualTo(1);
    }

    @Test
    void staleAdminFormCannotOverwriteStockChangedByCheckout() {
        Product stale = admin.detail(product.getId());
        checkout();
        stale.setName("古い編集内容");
        assertThatThrownBy(() -> admin.save(stale)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("更新されています");
        assertThat(productRepository.findById(product.getId()).orElseThrow().getStock()).isEqualTo(8);
        assertThat(productRepository.findById(product.getId()).orElseThrow().getName()).isEqualTo("ニット");
    }

    @Test
    void adminRejectsMissingImagesAndInvalidNumbers() throws Exception {
        Product invalid = product("検証用", 1000, 1); invalid.setImageUrl("/images/products/missing.png");
        assertThatThrownBy(() -> admin.save(invalid)).isInstanceOf(IllegalArgumentException.class);
        mvc.perform(post("/admin/products/save").with(user("manager").roles("ADMIN")).with(csrf())
                .param("name", "入力エラー").param("price", "text").param("stock", "-1"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("整数で入力")));
        mvc.perform(post("/admin/products/" + product.getId() + "/delete").with(user("alice")).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void failedLoginsAreRateLimited() throws Exception {
        for (int attempt = 0; attempt < 10; attempt++) {
            mvc.perform(post("/login").with(csrf()).with(request -> { request.setRemoteAddr("192.0.2.20"); return request; })
                    .param("userName", "alice").param("password", "wrong"))
                    .andExpect(redirectedUrl("/?error"));
        }
        mvc.perform(post("/login").with(csrf()).with(request -> { request.setRemoteAddr("192.0.2.20"); return request; })
                .param("userName", "alice").param("password", "password123"))
                .andExpect(redirectedUrl("/?limited"));
    }
}
