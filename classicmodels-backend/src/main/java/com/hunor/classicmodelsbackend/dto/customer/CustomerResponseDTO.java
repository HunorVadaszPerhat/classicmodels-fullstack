package com.hunor.classicmodelsbackend.dto.customer;

import java.math.BigDecimal;

public record CustomerResponseDTO(
        int customerNumber,
        String customerName,
        String contactLastName,
        String contactFirstName,
        String phone,
        String addressLine1,
        String addressLine2,
        String city,
        String state,
        String postalCode,
        String country,
        Integer salesRepEmployeeNumber,
        BigDecimal creditLimit
) {}
