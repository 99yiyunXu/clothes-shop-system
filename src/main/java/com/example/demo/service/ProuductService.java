package com.example.demo.service;

import java.util.List;
import org.springframework.stereotype.Service;
import com.example.demo.entity.Product;
import com.example.demo.repository.ProductRepository;
import org.springframework.data.domain.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class ProuductService {
    private final ProductRepository productRepository;

    public ProuductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    // 既存の一覧取得メソッドは件数を制限しない
    public List<Product> findProductList() {
        return searchProducts("", "");
    }

    // 商品名・カテゴリで検索する（新着順）
    public List<Product> searchProducts(String keyword, String category) {
        return query(keyword, category, false, Pageable.unpaged(newestSort())).getContent();
    }

    public Page<Product> searchPage(String keyword, String category, boolean sale, int page) {
        return query(keyword, category, sale, PageRequest.of(Math.max(0, page), 12, newestSort()));
    }

    private Sort newestSort() { return Sort.by(Sort.Direction.DESC, "createdAt", "id"); }

    private Page<Product> query(String keyword, String category, boolean sale, Pageable pageable) {
        String text = keyword == null ? "" : keyword.trim();
        if (text.length() > 100) throw new IllegalArgumentException("検索語は100文字以内にしてください。");
        String escaped = text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return productRepository.search(escaped, category == null ? "" : category.trim(), sale,
                pageable);
    }
    public List<Product> newest() { return productRepository.newest(PageRequest.of(0, 4)); }
    public List<Product> recommended() { return productRepository.recommended(); }
    public Product detail(Integer id) {
        return productRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}
