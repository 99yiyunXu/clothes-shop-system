package com.example.demo.controller;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class HomeController {

    // user
    private final List<User> users = List.of(
            new User("admin", "123456"),
            new User("luna", "131400"),
            new User("lihua", "1234"),
            new User("lisi", "5678"),
            new User("test", "test123")
    );

   
    @GetMapping("/")
    public String index() {
        return "index";
    }
    
    @GetMapping("/home")
    public String home() {
        return "home";
    }
    
    @PostMapping("/login")
    public String login(
            @RequestParam String userName,
            @RequestParam String password,
            Model model) {

        for (User user : users) {

            if (user.getUserName().equals(userName)
                    && user.getPassword().equals(password)) {

                model.addAttribute("userName", userName);

                return "home";
            }
        }

        model.addAttribute(
                "error",
                "ユーザー名またはパスワードが正しくありません。"
        );

        return "index";
    }

    
    static class User {

        private String userName;
        private String password;

        public User(String userName, String password) {
            this.userName = userName;
            this.password = password;
        }

        public String getUserName() {
            return userName;
        }

        public String getPassword() {
            return password;
        }
    }
}