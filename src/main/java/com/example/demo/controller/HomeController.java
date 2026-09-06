package com.example.demo.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class HomeController {

    @GetMapping("/")
    public String index() {
        return "index";
    }
    
    
    @GetMapping("/index2")
    @ResponseBody
    public String index2(
            @RequestParam String userName,
            @RequestParam String password) {

        if ("admin".equals(userName)
                && "password".equals(password)) {

            return "OK";
        } else {
            return "NG";
        }
    }
}
