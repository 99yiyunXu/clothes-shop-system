package com.example.demo.service;

import com.example.demo.entity.Product;
import com.example.demo.repository.ProductRepository;
import java.util.Set;
import java.util.Objects;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class AdminProductService {
    public static final Set<String> CATEGORIES = Set.of("レディース", "メンズ", "アクセサリー");
    private final ProductRepository products;
    private final ProductImageService images;
    public AdminProductService(ProductRepository products, ProductImageService images) {
        this.products = products; this.images = images;
    }
    public Page<Product> list(int page) {
        return products.findAll(PageRequest.of(Math.max(0, page), 12, Sort.by(Sort.Direction.DESC, "id")));
    }
    public Product detail(Integer id) {
        return products.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
    @Transactional
    public void save(Product input) {
        // 既存 MySQL の商品名列（VARCHAR(100)）に合わせ、保存前に長さを検証する。
        String name = OrderService.requiredText(input.getName(), 100, "商品名");
        if (input.getPrice() == null || input.getPrice() < 0 || input.getPrice() > 100_000_000)
            throw new IllegalArgumentException("価格は0～100000000円にしてください。");
        if (input.getStock() == null || input.getStock() < 0 || input.getStock() > 1_000_000)
            throw new IllegalArgumentException("在庫は0～1000000にしてください。");
        if (!CATEGORIES.contains(input.getCategory())) throw new IllegalArgumentException("分類を選択してください。");
        String image = images.forSave(input.getImageUrl());
        String description = input.getDescription() == null ? "" : input.getDescription().trim();
        if (description.length() > 2000) throw new IllegalArgumentException("説明は2000文字以内にしてください。");
        Product product = input.getId() == null ? new Product() : products.findLockedById(input.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (input.getId() != null && !Objects.equals(product.getVersion(), input.getVersion()))
            throw new IllegalArgumentException("商品または在庫が更新されています。編集画面を開き直してください。");
        product.setName(name); product.setPrice(input.getPrice()); product.setStock(input.getStock());
        product.setCategory(input.getCategory()); product.setImageUrl(image); product.setDescription(description);
        product.setActive(input.isActive()); product.setSale(input.isSale());
        products.save(product);
    }
    @Transactional
    public void deactivate(Integer id) {
        Product product = products.findLockedById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        // 注文履歴を保つため、物理削除せず販売終了にする
        product.setActive(false);
    }
}
