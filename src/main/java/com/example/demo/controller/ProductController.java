package com.example.demo.controller;

import java.security.Principal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import com.example.demo.service.ProuductService;
import com.example.demo.service.FavoriteService;

@Controller
public class ProductController {
    private final ProuductService productService;
    private final FavoriteService favorites;
    public ProductController(ProuductService productService, FavoriteService favorites) {
        this.productService = productService; this.favorites = favorites;
    }
    @GetMapping("/products")
    public String products(@RequestParam(defaultValue = "") String keyword,
                           @RequestParam(defaultValue = "") String category,
                           @RequestParam(defaultValue = "false") boolean sale,
                           @RequestParam(defaultValue = "0") int page, Model model) {
        var result = productService.searchPage(keyword, category, sale, page);
        model.addAttribute("products", result.getContent());
        model.addAttribute("productPage", result);
        model.addAttribute("keyword", keyword); model.addAttribute("category", category);
        model.addAttribute("sale", sale);
        return "products";
    }
    @GetMapping("/products/{id}")
    public String detail(@PathVariable Integer id, Principal principal, Model model) {
        model.addAttribute("product", productService.detail(id));
        model.addAttribute("favorite", favorites.contains(principal.getName(), id));
        return "product-detail";
    }
}
