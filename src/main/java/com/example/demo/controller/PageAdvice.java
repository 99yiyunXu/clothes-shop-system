package com.example.demo.controller;

import com.example.demo.service.ProductImageService;
import java.security.Principal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@ControllerAdvice
public class PageAdvice {
    private final ProductImageService images;

    public PageAdvice(ProductImageService images) { this.images = images; }

    @ModelAttribute("productImages")
    public ProductImageService productImages() { return images; }

    @ModelAttribute
    public void currentUser(Principal principal, HttpServletRequest request, Model model) {
        if (principal != null) model.addAttribute("currentUser", principal.getName());
        model.addAttribute("isAdmin", request.isUserInRole("ADMIN"));
    }
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String invalidInput(IllegalArgumentException ex, Model model) {
        model.addAttribute("error", ex.getMessage());
        return "error/400";
    }
}
