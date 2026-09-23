package com.example.demo.controller;

import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.example.demo.Product;
import com.example.demo.ProductRepository;

import jakarta.servlet.http.HttpSession;

@Controller
public class HomeController {

    private final ProductRepository productRepository;

    public HomeController(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    // ホーム画面を表示する
    @GetMapping("/home")
    public String home(HttpSession session, Model model) {

        // ログインしていない場合はログイン画面に戻る
        if (session.getAttribute("loginUser") == null) {
            return "redirect:/";
        }

        // 商品データを取得する
        List<Product> products = productRepository.findAll();

        // 新着商品を4件取得する
        List<Product> newProducts =
                products.stream()
                        .sorted((a, b) -> b.getId().compareTo(a.getId()))
                        .limit(4)
                        .toList();

        // おすすめ商品をランダムにする
        Collections.shuffle(products);

        List<Product> recommendedProducts =
                products.stream()
                        .limit(4)
                        .toList();

        // HTMLにデータを送る
        model.addAttribute("newProducts", newProducts);
        model.addAttribute("recommendedProducts", recommendedProducts);

        return "home";
    }
}