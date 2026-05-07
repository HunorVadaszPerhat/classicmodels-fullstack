package com.hunor.classicmodelsbackend.dto.order;

import java.time.LocalDate;

public record OrderRequestDTO(
        LocalDate orderDate,
        LocalDate requiredDate,
        LocalDate shippedDate,
        String status,
        String comments,
        int customerNumber
) {}
