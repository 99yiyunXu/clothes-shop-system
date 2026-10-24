package com.example.demo.service;

import com.example.demo.entity.*;
import com.example.demo.repository.*;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class FavoriteService {
    private final UserService users;
    private final FavoriteRepository favorites;
    private final ProuductService products;
    public FavoriteService(UserService users, FavoriteRepository favorites, ProuductService products) {
        this.users = users; this.favorites = favorites; this.products = products;
    }
    public Page<Favorite> list(String username, int page) {
        return favorites.findByUserIdAndProductActiveTrueOrderByIdDesc(users.require(username).getId(),
                PageRequest.of(Math.max(0, page), 12));
    }
    public boolean contains(String username, Integer productId) {
        return favorites.existsByUserIdAndProductId(users.require(username).getId(), productId);
    }
    @Transactional
    public void add(String username, Integer productId) {
        ShopUser user = users.lock(username);
        Product product = products.detail(productId);
        if (favorites.existsByUserIdAndProductId(user.getId(), productId)) return;
        Favorite favorite = new Favorite(); favorite.setUser(user); favorite.setProduct(product);
        favorites.save(favorite);
    }
    @Transactional
    public void remove(String username, Integer productId) {
        ShopUser user = users.lock(username);
        favorites.findByUserIdAndProductId(user.getId(), productId).ifPresent(favorites::delete);
    }
}
