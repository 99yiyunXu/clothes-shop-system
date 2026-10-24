package com.example.demo.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import com.example.demo.entity.Product;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface ProductRepository extends JpaRepository<Product, Integer> {
    @Query("select p from Product p where p.active = true and (:keyword = '' or lower(p.name) like lower(concat('%', :keyword, '%')) escape '\\') and (:category = '' or lower(p.category) = lower(:category)) and (:sale = false or p.sale = true)")
    Page<Product> search(@Param("keyword") String keyword, @Param("category") String category,
                         @Param("sale") boolean sale, Pageable pageable);

    @Query("select p from Product p where p.active = true order by p.createdAt desc, p.id desc")
    List<Product> newest(Pageable pageable);

    @Query(value = "select * from products where active = true order by RAND() limit 4", nativeQuery = true)
    List<Product> recommended();

    Optional<Product> findByIdAndActiveTrue(Integer id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id = :id")
    Optional<Product> findLockedById(@Param("id") Integer id);
    List<Product> findAllByOrderByIdDesc();
    List<Product> findByNameContainingIgnoreCaseOrderByIdDesc(String keyword);
    List<Product> findByCategoryIgnoreCaseOrderByIdDesc(String category);
    List<Product> findByNameContainingIgnoreCaseAndCategoryIgnoreCaseOrderByIdDesc(
            String keyword, String category);
}
