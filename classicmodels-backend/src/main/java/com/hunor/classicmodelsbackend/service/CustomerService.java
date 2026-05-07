package com.hunor.classicmodelsbackend.service;

import java.util.List;

import com.hunor.classicmodelsbackend.dto.customer.CustomerRequestDTO;
import com.hunor.classicmodelsbackend.dto.customer.CustomerResponseDTO;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import com.hunor.classicmodelsbackend.mapper.CustomerMapper;
import com.hunor.classicmodelsbackend.model.Customer;
import com.hunor.classicmodelsbackend.repository.CustomerRepository;
import com.hunor.classicmodelsbackend.repository.EmployeeRepository;
import com.hunor.classicmodelsbackend.response.PageResponse;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class CustomerService {

    private final CustomerRepository repo;
    private final CustomerMapper mapper;
    private final EmployeeRepository employeeRepo;

    public CustomerService(CustomerRepository repo, CustomerMapper mapper, EmployeeRepository employeeRepo) {
        this.repo = repo;
        this.mapper = mapper;
        this.employeeRepo = employeeRepo;
    }

    @Cacheable(cacheNames = "customersAll")
    public List<CustomerResponseDTO> findAll() {
        log.info("Finding all customers");
        long startTime = System.currentTimeMillis();

        var result = repo.findAll().stream().map(mapper::toResponseDTO).toList();

        long duration = System.currentTimeMillis() - startTime;
        log.info("Found {} customers in {} ms", result.size(), duration);

        return result;
    }

    @Cacheable(cacheNames = "customers", key = "#id")
    public CustomerResponseDTO findById(int id) {
        log.info("Finding a customer by ID: {}", id);
        long startTime = System.currentTimeMillis();

        var result = repo.findById(id)
                .map(mapper::toResponseDTO)
                .orElseThrow(() -> new RuntimeException("Customer " + id + " not found"));

        long duration = System.currentTimeMillis() - startTime;
        log.info("Found a customer by ID {} in {} ms", id, duration);

        return result;
    }

    @CachePut(cacheNames = "customers", key = "#result.customerNumber()")
    @CacheEvict(cacheNames = "customersAll", allEntries = true)
    public CustomerResponseDTO create(CustomerRequestDTO dto) {
        log.info("Creating a customer: {}", dto.customerName());
        long startTime = System.currentTimeMillis();

        if (dto.salesRepEmployeeNumber() != null) {
            log.debug("Validating sales rep employee {}", dto.salesRepEmployeeNumber());
            employeeRepo.findById(dto.salesRepEmployeeNumber())
                    .orElseThrow(() -> new RuntimeException(
                            "Employee " + dto.salesRepEmployeeNumber() + " (sales rep) not found"));
        }
        Customer saved = repo.save(mapper.toEntity(dto));
        CustomerResponseDTO responseDTO = mapper.toResponseDTO(saved);

        long duration = System.currentTimeMillis() - startTime;
        log.info("Created customer {} in {} ms", responseDTO.customerNumber(), duration);
        return responseDTO;
    }

    @CachePut(cacheNames = "customers", key = "#id")
    @CacheEvict(cacheNames = "customersAll", allEntries = true)
    public CustomerResponseDTO update(int id, CustomerRequestDTO dto) {
        log.info("Updating customer {}", id);
        long startTime = System.currentTimeMillis();

        var existing = repo.findById(id)
                .orElseThrow(() -> new RuntimeException("Customer " + id + " not found"));

        if (dto.salesRepEmployeeNumber() != null) {
            employeeRepo.findById(dto.salesRepEmployeeNumber())
                    .orElseThrow(() -> new RuntimeException(
                            "Employee " + dto.salesRepEmployeeNumber() + " (sales rep) not found"));
        }

        mapper.copyToEntity(dto, existing);
        existing.setCustomerNumber(id);
        repo.update(existing);
        var response = mapper.toResponseDTO(existing);

        long duration = System.currentTimeMillis() - startTime;
        log.info("Updated customer {} in {} ms", id, duration);
        return response;
    }

    @CacheEvict(cacheNames = {"customers", "customersAll"}, allEntries = true)
    public void delete(int id) {
        log.info("Deleting customer {}", id);
        long startTime = System.currentTimeMillis();

        repo.findById(id).orElseThrow(() -> new RuntimeException("Customer " + id + " not found"));
        repo.delete(id);

        long duration = System.currentTimeMillis() - startTime;
        log.info("Deleted customer {} in {} ms", id, duration);
    }

    @Cacheable(cacheNames = "customersPaged", key = "{#page, #size, #sortBy, #asc}")
    public PageResponse<CustomerResponseDTO> findAllPaged(int page, int size, String sortBy, boolean asc) {
        log.info("Finding customers page={} size={} sortBy={} asc={}", page, size, sortBy, asc);
        long startTime = System.currentTimeMillis();

        var items = repo.findAllPaged(page, size, sortBy, asc).stream().map(mapper::toResponseDTO).toList();
        long total = repo.countAll();
        int totalPages = (int) Math.ceil((double) total / size);
        var response = new PageResponse<>(items, page, size, total, totalPages);

        long duration = System.currentTimeMillis() - startTime;
        log.info("Found {} customers (page {}) in {} ms", items.size(), page, duration);
        return response;
    }

    @CacheEvict(cacheNames = "customersAll", allEntries = true)
    public void createBulk(List<CustomerRequestDTO> dtos) {
        log.info("Creating {} customers in bulk", dtos.size());
        long startTime = System.currentTimeMillis();

        var entities = dtos.stream().map(mapper::toEntity).toList();
        repo.saveAll(entities);

        long duration = System.currentTimeMillis() - startTime;
        log.info("Bulk created {} customers in {} ms", entities.size(), duration);
    }
}
