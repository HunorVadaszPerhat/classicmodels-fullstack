package com.hunor.classicmodelsbackend.service;

import com.hunor.classicmodelsbackend.dto.orderdetail.OrderDetailRequestDTO;
import com.hunor.classicmodelsbackend.dto.orderdetail.OrderDetailResponseDTO;
import com.hunor.classicmodelsbackend.mapper.OrderDetailMapper;
import com.hunor.classicmodelsbackend.model.OrderDetail;
import com.hunor.classicmodelsbackend.repository.OrderDetailRepository;
import com.hunor.classicmodelsbackend.repository.OrderRepository;
import com.hunor.classicmodelsbackend.repository.ProductRepository;
import com.hunor.classicmodelsbackend.response.PageResponse;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Service
@Slf4j
public class OrderDetailService {

    private final OrderDetailRepository repo;
    private final OrderDetailMapper mapper;
    private final OrderRepository orderRepo;
    private final ProductRepository productRepo;

    public OrderDetailService(OrderDetailRepository repo,
                              OrderDetailMapper mapper,
                              OrderRepository orderRepo,
                              ProductRepository productRepo) {
        this.repo = repo;
        this.mapper = mapper;
        this.orderRepo = orderRepo;
        this.productRepo = productRepo;
    }

    @Cacheable(cacheNames = "orderDetailsAll")
    public List<OrderDetailResponseDTO> findAll() {
        return repo.findAll().stream().map(mapper::toResponseDTO).toList();
    }

    @Cacheable(cacheNames = "orderDetails", key = "{#orderNumber, #productCode}")
    public OrderDetailResponseDTO findById(int orderNumber, String productCode) {
        return repo.findById(orderNumber, productCode)
                .map(mapper::toResponseDTO)
                .orElseThrow(() -> new RuntimeException(
                        "OrderDetail (" + orderNumber + ", " + productCode + ") not found"));
    }

    @CachePut(cacheNames = "orderDetails", key = "{#result.orderNumber(), #result.productCode()}")
    @CacheEvict(cacheNames = "orderDetailsAll", allEntries = true)
    public OrderDetailResponseDTO create(OrderDetailRequestDTO dto) {
        orderRepo.findById(dto.orderNumber())
                .orElseThrow(() -> new RuntimeException("Order " + dto.orderNumber() + " not found"));
        productRepo.findById(dto.productCode())
                .orElseThrow(() -> new RuntimeException("Product " + dto.productCode() + " not found"));

        OrderDetail saved = repo.save(mapper.toEntity(dto));
        return mapper.toResponseDTO(saved);
    }

    @CachePut(cacheNames = "orderDetails", key = "{#orderNumber, #productCode}")
    @CacheEvict(cacheNames = "orderDetailsAll", allEntries = true)
    public OrderDetailResponseDTO update(int orderNumber, String productCode, OrderDetailRequestDTO dto) {
        var existing = repo.findById(orderNumber, productCode)
                .orElseThrow(() -> new RuntimeException(
                        "OrderDetail (" + orderNumber + ", " + productCode + ") not found"));

        orderRepo.findById(orderNumber)
                .orElseThrow(() -> new RuntimeException("Order " + orderNumber + " not found"));
        productRepo.findById(productCode)
                .orElseThrow(() -> new RuntimeException("Product " + productCode + " not found"));

        mapper.copyToEntity(dto, existing);
        existing.setOrderNumber(orderNumber);
        existing.setProductCode(productCode);
        repo.update(existing);
        return mapper.toResponseDTO(existing);
    }

    @CacheEvict(cacheNames = {"orderDetails", "orderDetailsAll"}, allEntries = true)
    public void delete(int orderNumber, String productCode) {
        repo.findById(orderNumber, productCode)
                .orElseThrow(() -> new RuntimeException(
                        "OrderDetail (" + orderNumber + ", " + productCode + ") not found"));
        repo.delete(orderNumber, productCode);
    }

    @Cacheable(cacheNames = "orderDetailsPaged", key = "{#page, #size, #sortBy, #asc}")
    public PageResponse<OrderDetailResponseDTO> findAllPaged(int page, int size, String sortBy, boolean asc) {
        var items = repo.findAllPaged(page, size, sortBy, asc).stream().map(mapper::toResponseDTO).toList();
        long total = repo.countAll();
        int totalPages = (int) Math.ceil((double) total / size);
        return new PageResponse<>(items, page, size, total, totalPages);
    }

    @CacheEvict(cacheNames = "orderDetailsAll", allEntries = true)
    public void createBulk(List<OrderDetailRequestDTO> dtos) {
        dtos.stream().map(OrderDetailRequestDTO::orderNumber).distinct().forEach(oid ->
                orderRepo.findById(oid).orElseThrow(() -> new RuntimeException("Order " + oid + " not found"))
        );
        dtos.stream().map(OrderDetailRequestDTO::productCode).distinct().forEach(pcode ->
                productRepo.findById(pcode).orElseThrow(() -> new RuntimeException("Product " + pcode + " not found"))
        );
        var entities = dtos.stream().map(mapper::toEntity).toList();
        repo.saveAll(entities);
    }
}
