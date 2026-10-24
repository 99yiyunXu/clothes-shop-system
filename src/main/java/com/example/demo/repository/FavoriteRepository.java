package com.example.demo.repository;

import com.example.demo.entity.Favorite;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

public interface FavoriteRepository extends JpaRepository<Favorite, Long> {
    @EntityGraph(attributePaths = {"product"})
    Page<Favorite> findByUserIdAndProductActiveTrueOrderByIdDesc(Long userId, Pageable pageable);
    Optional<Favorite> findByUserIdAndProductId(Long userId, Integer productId);
    boolean existsByUserIdAndProductId(Long userId, Integer productId);
}
