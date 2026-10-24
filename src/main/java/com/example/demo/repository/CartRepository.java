package com.example.demo.repository;

import com.example.demo.entity.CartItem;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface CartRepository extends JpaRepository<CartItem, Long> {
    @EntityGraph(attributePaths = {"product"})
    List<CartItem> findByUserIdOrderByProductIdAsc(Long userId);
    Optional<CartItem> findByUserIdAndProductId(Long userId, Integer productId);
    Optional<CartItem> findByIdAndUserId(Long id, Long userId);
}
