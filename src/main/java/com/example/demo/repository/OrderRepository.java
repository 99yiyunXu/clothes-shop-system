package com.example.demo.repository;

import com.example.demo.entity.ShopOrder;
import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<ShopOrder, Long> {
    Page<ShopOrder> findByUserIdOrderByIdDesc(Long userId, Pageable pageable);
    Optional<ShopOrder> findByUserIdAndCheckoutToken(Long userId, String checkoutToken);
    @EntityGraph(attributePaths = {"items", "user"})
    Optional<ShopOrder> findWithItemsById(Long id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from ShopOrder o where o.id = :id")
    Optional<ShopOrder> findLockedById(@Param("id") Long id);
    @EntityGraph(attributePaths = {"user"})
    Page<ShopOrder> findAllByOrderByIdDesc(Pageable pageable);
}
