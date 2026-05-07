package com.hunor.classicmodelsbackend.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class Payment {
    private int customerNumber;
    private String checkNumber;
    private LocalDate paymentDate;
    private BigDecimal amount;
}
