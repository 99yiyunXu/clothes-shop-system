package com.example.demo.controller;

import com.example.demo.service.*;
import java.security.Principal;
import java.util.UUID;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class CartController {
    private final CartService carts;
    public CartController(CartService carts) { this.carts = carts; }
    @GetMapping("/cart")
    public String cart(Principal principal, Model model) {
        model.addAttribute("cart", carts.summary(principal.getName()));
        return "cart";
    }
    @PostMapping("/cart/add")
    public String add(@RequestParam Integer productId, @RequestParam(defaultValue = "1") int quantity,
                      Principal principal, RedirectAttributes redirect) {
        try {
            carts.add(principal.getName(), productId, quantity);
            redirect.addFlashAttribute("message", "カートに追加しました。");
            return "redirect:/cart";
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
            return "redirect:/products/" + productId;
        }
    }
    @PostMapping("/cart/{id}/update")
    public String update(@PathVariable Long id, @RequestParam int quantity, Principal principal,
                         RedirectAttributes redirect) {
        try { carts.update(principal.getName(), id, quantity); }
        catch (IllegalArgumentException ex) { redirect.addFlashAttribute("error", ex.getMessage()); }
        return "redirect:/cart";
    }
    @PostMapping("/cart/{id}/remove")
    public String remove(@PathVariable Long id, Principal principal) {
        carts.remove(principal.getName(), id);
        return "redirect:/cart";
    }
    @GetMapping("/checkout")
    public String checkout(Principal principal, HttpSession session, Model model) {
        var cart = carts.summary(principal.getName());
        if (cart.lines().isEmpty()) return "redirect:/cart";
        String token = (String) session.getAttribute("checkoutToken");
        if (token == null) { token = UUID.randomUUID().toString(); session.setAttribute("checkoutToken", token); }
        model.addAttribute("cart", cart); model.addAttribute("checkoutToken", token);
        return "checkout";
    }
}
