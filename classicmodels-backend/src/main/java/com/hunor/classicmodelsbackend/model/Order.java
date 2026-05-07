package com.hunor.classicmodelsbackend.model;

import lombok.Data;
import java.time.LocalDate;

@Data
public class Order {
    private int orderNumber;
    private LocalDate orderDate;
    private LocalDate requiredDate;
    private LocalDate shippedDate;
    private String status;
    private String comments;
    private int customerNumber;
}
