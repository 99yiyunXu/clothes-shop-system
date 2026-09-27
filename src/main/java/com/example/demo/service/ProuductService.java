package com.example.demo.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.example.demo.entity.Product;
import com.example.demo.repository.ProductRepository;

@Service
public class ProuductService {

    private final ProductRepository productRepository;

    public ProuductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public List<Product> findProductList() {
        return productRepository.findAll();
    }
}