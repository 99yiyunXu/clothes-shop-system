package com.example.demo.controller;

import com.example.demo.service.FavoriteService;
import java.security.Principal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
public class FavoriteController {
    private final FavoriteService favorites;
    public FavoriteController(FavoriteService favorites) { this.favorites = favorites; }
    @GetMapping("/favorites")
    public String list(@RequestParam(defaultValue = "0") int page, Principal principal, Model model) {
        model.addAttribute("favoritePage", favorites.list(principal.getName(), page));
        return "favorites";
    }
    @PostMapping("/favorites/{id}/add")
    public String add(@PathVariable Integer id, Principal principal) {
        favorites.add(principal.getName(), id);
        return "redirect:/products/" + id;
    }
    @PostMapping("/favorites/{id}/remove")
    public String remove(@PathVariable Integer id, Principal principal) {
        favorites.remove(principal.getName(), id);
        return "redirect:/favorites";
    }
}
