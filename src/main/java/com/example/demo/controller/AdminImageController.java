package com.example.demo.controller;

import com.example.demo.service.ProductImageStorage;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Map;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.*;

/** 既存の /admin/** の権限制御と CSRF 検証を利用し、商品データには触れない。 */
@RestController
@RequestMapping("/admin/product-images")
public class AdminImageController {
    private static final String COUNT = AdminImageController.class.getName() + ".count";
    private final ProductImageStorage storage;

    public AdminImageController(ProductImageStorage storage) { this.storage = storage; }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProductImageStorage.Stored> upload(MultipartHttpServletRequest request, HttpSession session)
            throws IOException {
        int count = request.getMultiFileMap().values().stream().mapToInt(java.util.List::size).sum();
        if (count != 1 || request.getFiles("file").size() != 1)
            throw new IllegalArgumentException("画像は一度に1件だけ選択してください。");
        synchronized (session) {
            Integer uploaded = (Integer) session.getAttribute(COUNT);
            if (uploaded != null && uploaded >= 20)
                throw new IllegalArgumentException("このログインでのアップロード上限は20件です。再ログインしてから続けてください。");
            var result = storage.store(request.getFile("file"));
            session.setAttribute(COUNT, uploaded == null ? 1 : uploaded + 1);
            return ResponseEntity.status(HttpStatus.CREATED).body(result);
        }
    }

    @ExceptionHandler({IllegalArgumentException.class, MaxUploadSizeExceededException.class})
    public ResponseEntity<Map<String, String>> invalid(Exception ex) {
        String message = ex instanceof MaxUploadSizeExceededException
                ? "画像は5 MB以下、リクエスト全体は6 MB以下にしてください。" : ex.getMessage();
        return ResponseEntity.badRequest().body(Map.of("message", message));
    }

    @ExceptionHandler(IOException.class)
    public ResponseEntity<Map<String, String>> failed() {
        return ResponseEntity.internalServerError().body(Map.of("message", "画像を保存できません。保存先の設定を確認してください。商品情報は変更されていません。"));
    }
}
