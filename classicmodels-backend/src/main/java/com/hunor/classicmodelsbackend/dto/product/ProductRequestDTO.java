package com.hunor.classicmodelsbackend.dto.product;

import java.math.BigDecimal;

public record ProductRequestDTO(
        String productCode,
        String productName,
        String productLine,
        String productScale,
        String productVendor,
        String productDescription,
        int quantityInStock,
        BigDecimal buyPrice,
        BigDecimal msrp
) {}
