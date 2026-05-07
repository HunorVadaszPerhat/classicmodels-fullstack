package com.hunor.classicmodelsbackend.dto.payment;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PaymentResponseDTO(
        int customerNumber,
        String checkNumber,
        LocalDate paymentDate,
        BigDecimal amount
) {}
