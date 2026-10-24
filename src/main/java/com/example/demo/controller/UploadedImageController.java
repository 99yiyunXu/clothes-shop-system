package com.example.demo.controller;

import com.example.demo.service.ProductImageStorage;
import java.util.concurrent.TimeUnit;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** 保存先全体を公開せず、検証済み画像だけを配信する。 */
@RestController
public class UploadedImageController {
    private final ProductImageStorage storage;

    public UploadedImageController(ProductImageStorage storage) { this.storage = storage; }

    @GetMapping("/images/uploads/{filename}")
    public ResponseEntity<FileSystemResource> image(@PathVariable String filename) {
        var file = storage.find(ProductImageStorage.PREFIX + filename)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return ResponseEntity.ok().contentType(filename.endsWith(".jpg") ? MediaType.IMAGE_JPEG : MediaType.IMAGE_PNG)
                .cacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic())
                .header("X-Content-Type-Options", "nosniff")
                .body(new FileSystemResource(file));
    }
}
