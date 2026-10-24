package com.example.demo.service;

import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

/** 商品画像のパスを検証し、未設定または参照できない画像を共通画像に置き換える。 */
@Service("productImages")
public class ProductImageService {
    public static final String PLACEHOLDER = "/images/common/no-image.svg";
    private final ResourceLoader resources;
    private final ProductImageStorage storage;

    public ProductImageService(ResourceLoader resources, ProductImageStorage storage) {
        this.resources = resources;
        this.storage = storage;
    }

    public boolean exists(String path) {
        if (path != null && path.startsWith(ProductImageStorage.PREFIX)) return storage.find(path).isPresent();
        return path != null && (PLACEHOLDER.equals(path)
                || path.matches("/images/[a-zA-Z0-9/_-]+\\.(png|jpg|jpeg|webp)"))
                && resources.getResource("classpath:static" + path).exists();
    }

    /** 表示時のみ代替し、商品や過去の注文に保存されたパスは変更しない。 */
    public String resolve(String path) {
        return exists(path) ? path : PLACEHOLDER;
    }

    /** 未設定は許可するが、入力された不正なパスは管理画面で修正を求める。 */
    public String forSave(String path) {
        if (path == null || path.isBlank()) return PLACEHOLDER;
        String value = path.trim();
        if (value.length() > 255 || !exists(value))
            throw new IllegalArgumentException("画像は /images/ 以下に存在する PNG・JPEG・WebP のパスを指定してください。");
        return value;
    }
}
