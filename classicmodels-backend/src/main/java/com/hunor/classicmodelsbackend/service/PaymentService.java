package com.hunor.classicmodelsbackend.service;

import com.hunor.classicmodelsbackend.dto.payment.PaymentRequestDTO;
import com.hunor.classicmodelsbackend.dto.payment.PaymentResponseDTO;
import com.hunor.classicmodelsbackend.mapper.PaymentMapper;
import com.hunor.classicmodelsbackend.model.Payment;
import com.hunor.classicmodelsbackend.repository.CustomerRepository;
import com.hunor.classicmodelsbackend.repository.PaymentRepository;
import com.hunor.classicmodelsbackend.response.PageResponse;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Service
@Slf4j
public class PaymentService {

    private final PaymentRepository repo;
    private final PaymentMapper mapper;
    private final CustomerRepository customerRepo;

    public PaymentService(PaymentRepository repo, PaymentMapper mapper, CustomerRepository customerRepo) {
        this.repo = repo;
        this.mapper = mapper;
        this.customerRepo = customerRepo;
    }

    @Cacheable(cacheNames = "paymentsAll")
    public List<PaymentResponseDTO> findAll() {
        return repo.findAll().stream().map(mapper::toResponseDTO).toList();
    }

    @Cacheable(cacheNames = "payments", key = "{#customerNumber, #checkNumber}")
    public PaymentResponseDTO findById(int customerNumber, String checkNumber) {
        return repo.findById(customerNumber, checkNumber)
                .map(mapper::toResponseDTO)
                .orElseThrow(() -> new RuntimeException(
                        "Payment (" + customerNumber + ", " + checkNumber + ") not found"));
    }

    @CachePut(cacheNames = "payments", key = "{#result.customerNumber(), #result.checkNumber()}")
    @CacheEvict(cacheNames = {"paymentsAll", "customerActivity", "customerCreditStatus", "customerCreditAlerts", "customerCreditAlertCounts"}, allEntries = true)
    public PaymentResponseDTO create(PaymentRequestDTO dto) {
        customerRepo.findById(dto.customerNumber())
                .orElseThrow(() -> new RuntimeException("Customer " + dto.customerNumber() + " not found"));

        Payment saved = repo.save(mapper.toEntity(dto));
        return mapper.toResponseDTO(saved);
    }

    @CachePut(cacheNames = "payments", key = "{#customerNumber, #checkNumber}")
    @CacheEvict(cacheNames = {"paymentsAll", "customerActivity", "customerCreditStatus", "customerCreditAlerts", "customerCreditAlertCounts"}, allEntries = true)
    public PaymentResponseDTO update(int customerNumber, String checkNumber, PaymentRequestDTO dto) {
        var existing = repo.findById(customerNumber, checkNumber)
                .orElseThrow(() -> new RuntimeException(
                        "Payment (" + customerNumber + ", " + checkNumber + ") not found"));

        customerRepo.findById(customerNumber)
                .orElseThrow(() -> new RuntimeException("Customer " + customerNumber + " not found"));

        mapper.copyToEntity(dto, existing);
        repo.update(existing);
        return mapper.toResponseDTO(existing);
    }

    @CacheEvict(cacheNames = {"payments", "paymentsAll", "customerActivity", "customerCreditStatus", "customerCreditAlerts", "customerCreditAlertCounts"}, allEntries = true)
    public void delete(int customerNumber, String checkNumber) {
        repo.findById(customerNumber, checkNumber)
                .orElseThrow(() -> new RuntimeException(
                        "Payment (" + customerNumber + ", " + checkNumber + ") not found"));
        repo.delete(customerNumber, checkNumber);
    }

    @Cacheable(cacheNames = "paymentsPaged", key = "{#page, #size, #sortBy, #asc}")
    public PageResponse<PaymentResponseDTO> findAllPaged(int page, int size, String sortBy, boolean asc) {
        var items = repo.findAllPaged(page, size, sortBy, asc).stream().map(mapper::toResponseDTO).toList();
        long total = repo.countAll();
        int totalPages = (int) Math.ceil((double) total / size);
        return new PageResponse<>(items, page, size, total, totalPages);
    }

    @CacheEvict(cacheNames = {"paymentsAll", "customerActivity", "customerCreditStatus", "customerCreditAlerts", "customerCreditAlertCounts"}, allEntries = true)
    public void createBulk(List<PaymentRequestDTO> dtos) {
        dtos.stream().map(PaymentRequestDTO::customerNumber).distinct().forEach(cid ->
                customerRepo.findById(cid).orElseThrow(() -> new RuntimeException("Customer " + cid + " not found"))
        );
        var entities = dtos.stream().map(mapper::toEntity).toList();
        repo.saveAll(entities);
    }
}
