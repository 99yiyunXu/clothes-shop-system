package com.example.demo.service;

import com.example.demo.entity.*;
import com.example.demo.entity.ShopOrder.Status;
import com.example.demo.repository.*;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.LockModeType;

@Service
@Transactional(readOnly = true)
public class OrderService {
    @PersistenceContext
    private EntityManager entityManager;
    private final UserService users;
    private final CartRepository carts;
    private final ProductRepository products;
    private final OrderRepository orders;
    public OrderService(UserService users, CartRepository carts, ProductRepository products, OrderRepository orders) {
        this.users = users; this.carts = carts; this.products = products; this.orders = orders;
    }

    @Transactional
    public Long checkout(String username, String token, String recipient, String address) {
        ShopUser user = users.lock(username);
        if (token == null || !token.matches("[0-9a-f-]{36}"))
            throw new IllegalArgumentException("注文画面を開き直してください。");
        Optional<ShopOrder> existing = orders.findByUserIdAndCheckoutToken(user.getId(), token);
        if (existing.isPresent()) return existing.get().getId();
        String name = requiredText(recipient, 100, "お名前");
        String destination = requiredText(address, 500, "配送先住所");
        List<CartItem> cart = carts.findByUserIdOrderByProductIdAsc(user.getId());
        if (cart.isEmpty()) throw new IllegalArgumentException("カートに商品がありません。");
        ShopOrder order = new ShopOrder();
        order.setUser(user); order.setCheckoutToken(token); order.setRecipient(name); order.setAddress(destination);
        long subtotal = 0;
        // 商品番号順にロックし、価格と在庫を注文時に再確認する
        for (CartItem line : cart) {
            Product product = line.getProduct();
            // カート取得時の古い値をロック付きで再読み込みする
            entityManager.refresh(product, LockModeType.PESSIMISTIC_WRITE);
            int quantity = line.getQuantity();
            if (!product.isActive() || quantity < 1 || quantity > 99 || product.getStock() < quantity)
                throw new IllegalArgumentException(product.getName() + "は販売終了または在庫不足です。");
            if (product.getPrice() == null || product.getPrice() < 0)
                throw new IllegalArgumentException("商品価格を確認してください。");
            OrderItem item = new OrderItem();
            item.setProductId(product.getId()); item.setProductName(product.getName());
            item.setImageUrl(product.getImageUrl()); item.setUnitPrice(product.getPrice()); item.setQuantity(quantity);
            order.addItem(item);
            subtotal += item.getLineTotal();
            product.setStock(product.getStock() - quantity);
        }
        order.setSubtotal(subtotal);
        order.setShippingFee(CartService.shippingFee(subtotal));
        order.setTotal(subtotal + order.getShippingFee());
        orders.saveAndFlush(order);
        carts.deleteAll(cart);
        return order.getId();
    }

    public Page<ShopOrder> list(String username, int page) {
        return orders.findByUserIdOrderByIdDesc(users.require(username).getId(), PageRequest.of(Math.max(0, page), 10));
    }
    public ShopOrder detail(String username, Long id) {
        ShopOrder order = orders.findWithItemsById(id).orElseThrow(OrderService::notFound);
        if (!order.getUser().getUsername().equals(username)) throw notFound();
        return order;
    }

    @Transactional
    public void payDemo(String username, Long id) {
        ShopOrder order = ownedLocked(username, id);
        if (order.getStatus() == Status.PAID) return;
        expect(order, Status.PENDING);
        // 実際の請求は行わず、学習用の支払い状態だけを更新する
        order.setStatus(Status.PAID); order.setPaidAt(LocalDateTime.now());
    }

    @Transactional
    public void cancel(String username, Long id) {
        ShopOrder order = ownedLocked(username, id);
        if (order.getStatus() == Status.CANCELLED) return;
        expect(order, Status.PENDING);
        restoreStock(order);
        order.setStatus(Status.CANCELLED);
    }

    @Transactional
    public void receive(String username, Long id) {
        ShopOrder order = ownedLocked(username, id);
        expect(order, Status.SHIPPED);
        order.setStatus(Status.COMPLETED);
    }

    @Transactional
    public void requestReturn(String username, Long id, String reason) {
        ShopOrder order = ownedLocked(username, id);
        if (order.getStatus() != Status.SHIPPED && order.getStatus() != Status.COMPLETED)
            throw new IllegalArgumentException("発送済みまたは受取済みの注文のみ返品を申請できます。");
        order.setReturnReason(requiredText(reason, 500, "返品理由"));
        order.setStatus(Status.RETURN_REQUESTED);
    }

    public Page<ShopOrder> adminList(int page) {
        return orders.findAllByOrderByIdDesc(PageRequest.of(Math.max(0, page), 10));
    }
    public ShopOrder adminDetail(Long id) {
        return orders.findWithItemsById(id).orElseThrow(OrderService::notFound);
    }

    @Transactional
    public void ship(Long id, String trackingNumber) {
        ShopOrder order = orders.findLockedById(id).orElseThrow(OrderService::notFound);
        expect(order, Status.PAID);
        order.setTrackingNumber(requiredText(trackingNumber, 100, "追跡番号"));
        order.setStatus(Status.SHIPPED);
    }

    @Transactional
    public void approveReturn(Long id) {
        ShopOrder order = orders.findLockedById(id).orElseThrow(OrderService::notFound);
        if (order.getStatus() == Status.RETURNED) return;
        expect(order, Status.RETURN_REQUESTED);
        restoreStock(order);
        order.setStatus(Status.RETURNED);
    }

    private ShopOrder ownedLocked(String username, Long id) {
        ShopOrder order = orders.findLockedById(id).orElseThrow(OrderService::notFound);
        if (!order.getUser().getUsername().equals(username)) throw notFound();
        return order;
    }
    private void restoreStock(ShopOrder order) {
        order.getItems().stream().sorted(Comparator.comparing(OrderItem::getProductId)).forEach(item -> {
            Product product = products.findLockedById(item.getProductId())
                    .orElseThrow(() -> new IllegalArgumentException("在庫を戻す商品が見つかりません。"));
            product.setStock(Math.addExact(product.getStock(), item.getQuantity()));
        });
    }
    private static void expect(ShopOrder order, Status expected) {
        if (order.getStatus() != expected) throw new IllegalArgumentException("現在の注文状態では操作できません。");
    }
    public static String requiredText(String value, int max, String label) {
        if (value == null || value.isBlank() || value.trim().length() > max)
            throw new IllegalArgumentException(label + "を1～" + max + "文字で入力してください。");
        return value.trim();
    }
    private static ResponseStatusException notFound() { return new ResponseStatusException(HttpStatus.NOT_FOUND); }
}
