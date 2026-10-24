package com.example.demo.service;

import com.example.demo.entity.*;
import com.example.demo.repository.*;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class CartService {
    private final UserService users;
    private final CartRepository carts;
    private final ProductRepository products;
    public CartService(UserService users, CartRepository carts, ProductRepository products) {
        this.users = users; this.carts = carts; this.products = products;
    }
    public record Line(Long id, Integer productId, String name, String imageUrl, int price,
                       int quantity, int stock, boolean active) {
        public long getLineTotal() { return (long) price * quantity; }
    }
    public record Summary(List<Line> lines, long subtotal, long shippingFee, long total) {}
    public Summary summary(String username) {
        List<Line> lines = carts.findByUserIdOrderByProductIdAsc(users.require(username).getId()).stream()
            .map(item -> new Line(item.getId(), item.getProduct().getId(), item.getProduct().getName(),
                item.getProduct().getImageUrl(), item.getProduct().getPrice(), item.getQuantity(),
                item.getProduct().getStock(), item.getProduct().isActive())).toList();
        long subtotal = lines.stream().mapToLong(Line::getLineTotal).sum();
        long fee = lines.isEmpty() ? 0 : shippingFee(subtotal);
        return new Summary(lines, subtotal, fee, subtotal + fee);
    }
    public static long shippingFee(long subtotal) { return subtotal >= 10_000 ? 0 : 500; }

    @Transactional
    public void add(String username, Integer productId, int quantity) {
        validateQuantity(quantity);
        ShopUser user = users.lock(username);
        Product product = products.findByIdAndActiveTrue(productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        CartItem item = carts.findByUserIdAndProductId(user.getId(), productId).orElseGet(CartItem::new);
        int updated = item.getQuantity() + quantity;
        validateQuantity(updated);
        if (product.getStock() < updated) throw new IllegalArgumentException("在庫数を超えています。");
        item.setUser(user); item.setProduct(product); item.setQuantity(updated);
        carts.save(item);
    }

    @Transactional
    public void update(String username, Long itemId, int quantity) {
        validateQuantity(quantity);
        ShopUser user = users.lock(username);
        CartItem item = owned(itemId, user.getId());
        if (!item.getProduct().isActive() || item.getProduct().getStock() < quantity)
            throw new IllegalArgumentException("販売終了または在庫不足です。");
        item.setQuantity(quantity);
    }

    @Transactional
    public void remove(String username, Long itemId) {
        ShopUser user = users.lock(username);
        carts.delete(owned(itemId, user.getId()));
    }
    private CartItem owned(Long itemId, Long userId) {
        return carts.findByIdAndUserId(itemId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
    private void validateQuantity(int quantity) {
        if (quantity < 1 || quantity > 99) throw new IllegalArgumentException("数量は1～99にしてください。");
    }
}
