package com.hunor.classicmodelsbackend.service;

import com.hunor.classicmodelsbackend.dto.order.OrderRequestDTO;
import com.hunor.classicmodelsbackend.dto.order.OrderResponseDTO;
import com.hunor.classicmodelsbackend.mapper.OrderMapper;
import com.hunor.classicmodelsbackend.model.Order;
import com.hunor.classicmodelsbackend.repository.CustomerRepository;
import com.hunor.classicmodelsbackend.repository.OrderRepository;
import com.hunor.classicmodelsbackend.response.PageResponse;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Service
@Slf4j
public class OrderService {

    private final OrderRepository repo;
    private final OrderMapper mapper;
    private final CustomerRepository customerRepo;

    public OrderService(OrderRepository repo, OrderMapper mapper, CustomerRepository customerRepo) {
        this.repo = repo;
        this.mapper = mapper;
        this.customerRepo = customerRepo;
    }

    @Cacheable(cacheNames = "ordersAll")
    public List<OrderResponseDTO> findAll() {
        return repo.findAll().stream().map(mapper::toResponseDTO).toList();
    }

    @Cacheable(cacheNames = "orders", key = "#id")
    public OrderResponseDTO findById(int id) {
        return repo.findById(id)
                .map(mapper::toResponseDTO)
                .orElseThrow(() -> new RuntimeException("Order " + id + " not found"));
    }

    @CachePut(cacheNames = "orders", key = "#result.orderNumber()")
    @CacheEvict(cacheNames = "ordersAll", allEntries = true)
    public OrderResponseDTO create(OrderRequestDTO dto) {
        customerRepo.findById(dto.customerNumber())
                .orElseThrow(() -> new RuntimeException("Customer " + dto.customerNumber() + " not found"));

        Order saved = repo.save(mapper.toEntity(dto));
        return mapper.toResponseDTO(saved);
    }

    @CachePut(cacheNames = "orders", key = "#id")
    @CacheEvict(cacheNames = "ordersAll", allEntries = true)
    public OrderResponseDTO update(int id, OrderRequestDTO dto) {
        var existing = repo.findById(id)
                .orElseThrow(() -> new RuntimeException("Order " + id + " not found"));

        customerRepo.findById(dto.customerNumber())
                .orElseThrow(() -> new RuntimeException("Customer " + dto.customerNumber() + " not found"));

        mapper.copyToEntity(dto, existing);
        existing.setOrderNumber(id);
        repo.update(existing);
        return mapper.toResponseDTO(existing);
    }

    @CacheEvict(cacheNames = {"orders", "ordersAll"}, allEntries = true)
    public void delete(int id) {
        repo.findById(id).orElseThrow(() -> new RuntimeException("Order " + id + " not found"));
        repo.delete(id);
    }

    @Cacheable(cacheNames = "ordersPaged", key = "{#page, #size, #sortBy, #asc}")
    public PageResponse<OrderResponseDTO> findAllPaged(int page, int size, String sortBy, boolean asc) {
        var items = repo.findAllPaged(page, size, sortBy, asc).stream().map(mapper::toResponseDTO).toList();
        long total = repo.countAll();
        int totalPages = (int) Math.ceil((double) total / size);
        return new PageResponse<>(items, page, size, total, totalPages);
    }

    @CacheEvict(cacheNames = "ordersAll", allEntries = true)
    public void createBulk(List<OrderRequestDTO> dtos) {

        dtos.stream().map(OrderRequestDTO::customerNumber).distinct().forEach(cid ->
                customerRepo.findById(cid).orElseThrow(() -> new RuntimeException("Customer " + cid + " not found"))
        );
        var entities = dtos.stream().map(mapper::toEntity).toList();
        repo.saveAll(entities);
    }
}
