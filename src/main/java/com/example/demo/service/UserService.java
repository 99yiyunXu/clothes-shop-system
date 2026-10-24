package com.example.demo.service;

import com.example.demo.entity.ShopUser;
import com.example.demo.repository.UserRepository;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.Locale;
import java.nio.charset.StandardCharsets;

@Service
public class UserService implements UserDetailsService {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    public UserService(UserRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) {
        ShopUser user = users.findByUsername(normalize(username))
                .orElseThrow(() -> new UsernameNotFoundException("ユーザーが見つかりません。"));
        return User.withUsername(user.getUsername()).password(user.getPasswordHash()).roles(user.getRole()).build();
    }
    @Transactional
    public void register(String username, String password, String confirmation) {
        String name = normalize(username);
        if (!name.matches("[a-z0-9_]{3,50}"))
            throw new IllegalArgumentException("ユーザー名は半角英数字と下線で3～50文字にしてください。");
        if (password == null || password.length() < 8 || password.length() > 64)
            throw new IllegalArgumentException("パスワードは8～64文字にしてください。");
        if (password.getBytes(StandardCharsets.UTF_8).length > 72)
            throw new IllegalArgumentException("パスワードが長すぎます。短い文字列で再度お試しください。");
        if (!password.equals(confirmation)) throw new IllegalArgumentException("確認用パスワードが一致しません。");
        if (users.existsByUsername(name)) throw new IllegalArgumentException("このユーザー名は使用されています。");
        ShopUser user = new ShopUser();
        user.setUsername(name);
        user.setPasswordHash(encoder.encode(password));
        users.saveAndFlush(user);
    }
    @Transactional(readOnly = true)
    public ShopUser require(String username) {
        return users.findByUsername(normalize(username))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }
    public ShopUser lock(String username) {
        return users.findLockedByUsername(normalize(username))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }
    public static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
