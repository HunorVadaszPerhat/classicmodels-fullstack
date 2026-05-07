package com.hunor.classicmodelsbackend.dto.orders_custom;

import java.math.BigDecimal;
import java.time.LocalDate;

public record LateOrderResponseDTO(
        int orderNumber,
        LocalDate orderDate,
        LocalDate requiredDate,
        LocalDate shippedDate,
        int customerNumber,
        String customerName,
        BigDecimal creditLimit,
        Integer salesRep,
        String salesRepName,
        int daysLate,
        String severity
) {}
