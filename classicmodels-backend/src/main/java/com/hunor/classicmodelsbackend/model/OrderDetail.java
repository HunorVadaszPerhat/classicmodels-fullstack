package com.hunor.classicmodelsbackend.model;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class OrderDetail {
    private int orderNumber;
    private String productCode;
    private int quantityOrdered;
    private BigDecimal priceEach;
    private int orderLineNumber;
}
