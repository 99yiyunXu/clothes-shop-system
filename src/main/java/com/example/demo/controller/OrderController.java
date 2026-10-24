package com.example.demo.controller;

import com.example.demo.service.OrderService;
import java.security.Principal;
import java.util.Objects;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class OrderController {
    private final OrderService orders;
    public OrderController(OrderService orders) { this.orders = orders; }
    @PostMapping("/orders")
    public String create(@RequestParam String checkoutToken, @RequestParam String recipient,
                         @RequestParam String address, Principal principal, HttpSession session,
                         RedirectAttributes redirect) {
        if (!Objects.equals(checkoutToken, session.getAttribute("checkoutToken")))
            throw new IllegalArgumentException("注文画面を開き直してください。");
        try {
            Long id = orders.checkout(principal.getName(), checkoutToken, recipient, address);
            session.removeAttribute("checkoutToken");
            return "redirect:/orders/" + id;
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
            redirect.addFlashAttribute("recipient", recipient); redirect.addFlashAttribute("address", address);
            return "redirect:/checkout";
        }
    }
    @GetMapping("/orders")
    public String list(@RequestParam(defaultValue = "0") int page, Principal principal, Model model) {
        model.addAttribute("orderPage", orders.list(principal.getName(), page));
        model.addAttribute("adminView", false);
        return "orders";
    }
    @GetMapping("/orders/{id}")
    public String detail(@PathVariable Long id, Principal principal, Model model) {
        model.addAttribute("order", orders.detail(principal.getName(), id));
        model.addAttribute("adminView", false);
        return "order-detail";
    }
    @PostMapping("/orders/{id}/pay-demo")
    public String pay(@PathVariable Long id, Principal principal, RedirectAttributes redirect) {
        return action(id, redirect, () -> orders.payDemo(principal.getName(), id), "デモ支払いが完了しました。実際の請求はありません。");
    }
    @PostMapping("/orders/{id}/cancel")
    public String cancel(@PathVariable Long id, Principal principal, RedirectAttributes redirect) {
        return action(id, redirect, () -> orders.cancel(principal.getName(), id), "注文をキャンセルしました。");
    }
    @PostMapping("/orders/{id}/receive")
    public String receive(@PathVariable Long id, Principal principal, RedirectAttributes redirect) {
        return action(id, redirect, () -> orders.receive(principal.getName(), id), "受け取りを確認しました。");
    }
    @PostMapping("/orders/{id}/return")
    public String requestReturn(@PathVariable Long id, @RequestParam String reason, Principal principal,
                                RedirectAttributes redirect) {
        return action(id, redirect, () -> orders.requestReturn(principal.getName(), id, reason), "返品を申請しました。");
    }
    private String action(Long id, RedirectAttributes redirect, Runnable action, String message) {
        try { action.run(); redirect.addFlashAttribute("message", message); }
        catch (IllegalArgumentException ex) { redirect.addFlashAttribute("error", ex.getMessage()); }
        return "redirect:/orders/" + id;
    }
}
