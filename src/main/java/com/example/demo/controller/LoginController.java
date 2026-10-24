package com.example.demo.controller;

import com.example.demo.service.UserService;
import java.security.Principal;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class LoginController {
    private final UserService users;
    public LoginController(UserService users) { this.users = users; }

    @GetMapping("/")
    public String index(Principal principal) { return principal == null ? "index" : "redirect:/home"; }

    @GetMapping("/register")
    public String registerPage() { return "register"; }

    @PostMapping("/register")
    public String register(@RequestParam String userName, @RequestParam String password,
                           @RequestParam String confirmation, Model model, RedirectAttributes redirect) {
        try {
            users.register(userName, password, confirmation);
            redirect.addFlashAttribute("message", "登録しました。ログインしてください。");
            return "redirect:/";
        } catch (IllegalArgumentException ex) {
            model.addAttribute("error", ex.getMessage());
        } catch (DataIntegrityViolationException ex) {
            model.addAttribute("error", "このユーザー名は使用されています。");
        }
        model.addAttribute("userName", userName);
        return "register";
    }

    @GetMapping("/success")
    public String success(Principal principal, Model model) {
        model.addAttribute("userName", principal.getName());
        return "success";
    }
}
