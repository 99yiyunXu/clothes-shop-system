package com.example.demo.repository;

import com.example.demo.entity.ShopUser;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<ShopUser, Long> {
    Optional<ShopUser> findByUsername(String username);
    boolean existsByUsername(String username);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from ShopUser u where u.username = :username")
    Optional<ShopUser> findLockedByUsername(@Param("username") String username);
}
