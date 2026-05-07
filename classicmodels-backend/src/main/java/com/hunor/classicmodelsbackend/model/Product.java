package com.hunor.classicmodelsbackend.model;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class Product {
    private String productCode;
    private String productName;
    private String productLine;
    private String productScale;
    private String productVendor;
    private String productDescription;
    private int quantityInStock;
    private BigDecimal buyPrice;
    private BigDecimal msrp;
}
