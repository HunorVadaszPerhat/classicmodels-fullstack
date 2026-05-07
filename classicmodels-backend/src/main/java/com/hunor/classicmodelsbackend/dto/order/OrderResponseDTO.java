package com.hunor.classicmodelsbackend.dto.order;

import java.time.LocalDate;

public record OrderResponseDTO(
        int orderNumber,
        LocalDate orderDate,
        LocalDate requiredDate,
        LocalDate shippedDate,
        String status,
        String comments,
        int customerNumber
) {}
