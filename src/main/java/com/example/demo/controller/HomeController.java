package com.example.demo.controller;

import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import com.example.demo.service.ProuductService;
import com.example.demo.service.ProductImageService;

@Controller
public class HomeController {
    private final ProuductService prouductService;
    public HomeController(ProuductService prouductService) { this.prouductService = prouductService; }
    public record Banner(String imageUrl, String subTitle, String title, String link) {}
    public record Category(String imageUrl, String name) {}

    @GetMapping("/home")
    public String home(Model model) {
        model.addAttribute("newProducts", prouductService.newest());
        model.addAttribute("recommendedProducts", prouductService.recommended());
        model.addAttribute("subBanners", List.of(
            new Banner("/images/woman2.png", "NEW COLLECTION", "新しい季節の装い", "/products"),
            new Banner("/images/woman3.png", "SALE", "セール商品を見る", "/products?sale=true")));
        model.addAttribute("categories", List.of(
            new Category("/images/products/knit1.png", "レディース"),
            new Category("/images/products/coat1.png", "メンズ"),
            new Category(ProductImageService.PLACEHOLDER, "アクセサリー")));
        return "home";
    }
}
