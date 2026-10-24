package com.example.demo.controller;

import com.example.demo.entity.Product;
import com.example.demo.service.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin")
public class AdminController {
    private final AdminProductService products;
    private final OrderService orders;
    public AdminController(AdminProductService products, OrderService orders) { this.products = products; this.orders = orders; }
    // 共通ナビゲーションの戻るリンクと配色を管理画面に限定する。
    @ModelAttribute("isAdminPage")
    public boolean isAdminPage() { return true; }

    @InitBinder("product")
    void productBinder(WebDataBinder binder) {
        binder.setAllowedFields("id", "version", "name", "price", "imageUrl", "category", "description", "stock", "active", "sale");
    }
    @GetMapping({"", "/products"})
    public String list(@RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("productPage", products.list(page));
        return "admin-products";
    }
    @GetMapping("/products/new")
    public String create(Model model) { model.addAttribute("product", new Product()); return "admin-product-form"; }
    @GetMapping("/products/{id}/edit")
    public String edit(@PathVariable Integer id, Model model) {
        model.addAttribute("product", products.detail(id)); return "admin-product-form";
    }
    @PostMapping("/products/save")
    public String save(@ModelAttribute("product") Product product, BindingResult binding, Model model,
                       RedirectAttributes redirect) {
        if (binding.hasErrors()) {
            model.addAttribute("error", "価格と在庫は整数で入力してください。"); return "admin-product-form";
        }
        try {
            boolean creating = product.getId() == null;
            products.save(product);
            // 保存結果と公開範囲を明示し、ホーム画面の個別設定が不要であることを伝える。
            String result = creating ? "商品を追加しました。" : "商品を更新しました。";
            String visibility = product.isActive()
                    ? "公開中です。商品一覧・検索に表示され、ホームのおすすめ候補になります。"
                    : "非公開で保存しました。商品一覧・検索・ホームのおすすめには表示されません。";
            redirect.addFlashAttribute("message", result + visibility);
            return "redirect:/admin/products";
        } catch (IllegalArgumentException ex) {
            model.addAttribute("error", ex.getMessage()); return "admin-product-form";
        }
    }
    @PostMapping("/products/{id}/delete")
    public String delete(@PathVariable Integer id, RedirectAttributes redirect) {
        products.deactivate(id);
        redirect.addFlashAttribute("message", "商品を非公開（販売終了）にしました。商品データと注文履歴は保持されます。再公開するには「編集・再公開」から公開設定を変更してください。");
        return "redirect:/admin/products";
    }
    @GetMapping("/orders")
    public String orders(@RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("orderPage", orders.adminList(page)); model.addAttribute("adminView", true);
        return "orders";
    }
    @GetMapping("/orders/{id}")
    public String order(@PathVariable Long id, Model model) {
        model.addAttribute("order", orders.adminDetail(id)); model.addAttribute("adminView", true);
        return "order-detail";
    }
    @PostMapping("/orders/{id}/ship")
    public String ship(@PathVariable Long id, @RequestParam String trackingNumber, RedirectAttributes redirect) {
        return action(id, redirect, () -> orders.ship(id, trackingNumber), "発送を登録しました。");
    }
    @PostMapping("/orders/{id}/return")
    public String approve(@PathVariable Long id, RedirectAttributes redirect) {
        return action(id, redirect, () -> orders.approveReturn(id), "返品とデモ返金を完了しました。");
    }
    private String action(Long id, RedirectAttributes redirect, Runnable action, String message) {
        try { action.run(); redirect.addFlashAttribute("message", message); }
        catch (IllegalArgumentException ex) { redirect.addFlashAttribute("error", ex.getMessage()); }
        return "redirect:/admin/orders/" + id;
    }
}
