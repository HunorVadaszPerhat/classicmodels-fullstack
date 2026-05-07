package com.hunor.classicmodelsbackend.dto.orderdetail;

import java.math.BigDecimal;

public record OrderDetailResponseDTO(
        int orderNumber,
        String productCode,
        int quantityOrdered,
        BigDecimal priceEach,
        int orderLineNumber
) {}
