package com.example.demo.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "shop_orders", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "checkout_token"}))
public class ShopOrder {
    public enum Status {
        PENDING("支払い待ち"), PAID("支払い済み"), SHIPPED("発送済み"), COMPLETED("受取済み"),
        CANCELLED("キャンセル済み"), RETURN_REQUESTED("返品申請中"), RETURNED("返品・返金済み");
        private final String label;
        Status(String label) { this.label = label; }
        public String getLabel() { return label; }
    }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private ShopUser user;
    @Column(nullable = false, length = 36)
    private String checkoutToken;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private Status status = Status.PENDING;
    @Column(nullable = false)
    private long subtotal;
    @Column(nullable = false)
    private long shippingFee;
    @Column(nullable = false)
    private long total;
    @Column(nullable = false, length = 100)
    private String recipient;
    @Column(nullable = false, length = 500)
    private String address;
    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime paidAt;
    @Column(length = 100)
    private String trackingNumber;
    @Column(length = 500)
    private String returnReason;
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<OrderItem> items = new ArrayList<>();

    public Long getId() { return id; }
    public ShopUser getUser() { return user; }
    public void setUser(ShopUser user) { this.user = user; }
    public String getCheckoutToken() { return checkoutToken; }
    public void setCheckoutToken(String checkoutToken) { this.checkoutToken = checkoutToken; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public long getSubtotal() { return subtotal; }
    public void setSubtotal(long subtotal) { this.subtotal = subtotal; }
    public long getShippingFee() { return shippingFee; }
    public void setShippingFee(long shippingFee) { this.shippingFee = shippingFee; }
    public long getTotal() { return total; }
    public void setTotal(long total) { this.total = total; }
    public String getRecipient() { return recipient; }
    public void setRecipient(String recipient) { this.recipient = recipient; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getPaidAt() { return paidAt; }
    public void setPaidAt(LocalDateTime paidAt) { this.paidAt = paidAt; }
    public String getTrackingNumber() { return trackingNumber; }
    public void setTrackingNumber(String trackingNumber) { this.trackingNumber = trackingNumber; }
    public String getReturnReason() { return returnReason; }
    public void setReturnReason(String returnReason) { this.returnReason = returnReason; }
    public List<OrderItem> getItems() { return items; }
    public void addItem(OrderItem item) { item.setOrder(this); items.add(item); }
}
