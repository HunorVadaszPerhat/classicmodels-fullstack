package com.hunor.classicmodelsbackend.dto.office;

public record OfficeRequestDTO(
        String city,
        String phone,
        String addressLine1,
        String addressLine2,
        String state,
        String country,
        String postalCode,
        String territory
) {}
