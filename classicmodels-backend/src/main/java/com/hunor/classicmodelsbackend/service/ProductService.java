package com.hunor.classicmodelsbackend.service;

import com.hunor.classicmodelsbackend.dto.product.ProductRequestDTO;
import com.hunor.classicmodelsbackend.dto.product.ProductResponseDTO;
import com.hunor.classicmodelsbackend.mapper.ProductMapper;
import com.hunor.classicmodelsbackend.model.Product;
import com.hunor.classicmodelsbackend.repository.ProductRepository;
import com.hunor.classicmodelsbackend.response.PageResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class ProductService {

    private final ProductRepository repo;
    private final ProductMapper mapper;

    public ProductService(ProductRepository repo, ProductMapper mapper) {
        this.repo = repo;
        this.mapper = mapper;
    }

    @Cacheable(cacheNames = "productsAll")
    public List<ProductResponseDTO> findAll() {
        return repo.findAll().stream().map(mapper::toResponseDTO).toList();
    }

    @Cacheable(cacheNames = "products", key = "#code")
    public ProductResponseDTO findById(String code) {
        return repo.findById(code)
                .map(mapper::toResponseDTO)
                .orElseThrow(() -> new RuntimeException("Product " + code + " not found"));
    }

    @CachePut(cacheNames = "products", key = "#result.productCode()")
    @CacheEvict(cacheNames = "productsAll", allEntries = true)
    public ProductResponseDTO create(ProductRequestDTO dto) {
        Product saved = repo.save(mapper.toEntity(dto));
        return mapper.toResponseDTO(saved);
    }

    @CachePut(cacheNames = "products", key = "#code")
    @CacheEvict(cacheNames = "productsAll", allEntries = true)
    public ProductResponseDTO update(String code, ProductRequestDTO dto) {
        var existing = repo.findById(code)
                .orElseThrow(() -> new RuntimeException("Product " + code + " not found"));
        mapper.copyToEntity(dto, existing);
        existing.setProductCode(code);
        repo.update(existing);
        return mapper.toResponseDTO(existing);
    }

    @CacheEvict(cacheNames = {"products", "productsAll"}, allEntries = true)
    public void delete(String code) {
        repo.findById(code).orElseThrow(() -> new RuntimeException("Product " + code + " not found"));
        repo.delete(code);
    }

    @Cacheable(cacheNames = "productsPaged", key = "{#page, #size, #sortBy, #asc}")
    public PageResponse<ProductResponseDTO> findAllPaged(int page, int size, String sortBy, boolean asc) {
        var items = repo.findAllPaged(page, size, sortBy, asc).stream()
                .map(mapper::toResponseDTO)
                .toList();
        long total = repo.countAll();
        int totalPages = (int) Math.ceil((double) total / (double) size);
        return new PageResponse<>(items, page, size, total, totalPages);
    }

    @CacheEvict(cacheNames = "productsAll", allEntries = true)
    public void createBulk(List<ProductRequestDTO> dtos) {
        var entities = dtos.stream().map(mapper::toEntity).toList();
        repo.saveAll(entities);
    }
}
