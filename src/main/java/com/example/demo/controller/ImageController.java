package com.example.demo.controller;

import java.util.List;
import java.util.Random;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ImageController {

    @GetMapping("/api/random-image")
    public String getRandomImage() {

        List<String> images = List.of(
            "/images/woman1.png",
            "/images/woman2.png",
            "/images/woman3.png",
            "/images/woman4.png"
        );

        Random random = new Random();

        return images.get(random.nextInt(images.size()));
    }
}