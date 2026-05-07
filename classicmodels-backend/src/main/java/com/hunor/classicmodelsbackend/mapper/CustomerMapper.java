package com.hunor.classicmodelsbackend.mapper;

import com.hunor.classicmodelsbackend.dto.customer.CustomerRequestDTO;
import com.hunor.classicmodelsbackend.dto.customer.CustomerResponseDTO;
import com.hunor.classicmodelsbackend.model.Customer;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface CustomerMapper {
    @Mapping(target = "customerNumber", ignore = true)
    Customer toEntity(CustomerRequestDTO dto);

    CustomerResponseDTO toResponseDTO(Customer c);

    void copyToEntity(CustomerRequestDTO dto, @MappingTarget Customer entity);
}

/* @Component
public class CustomerMapper {
    public Customer toEntity(CustomerRequestDTO dto) {
        if (dto == null) return null;
        Customer c = new Customer();
        copyToEntity(dto, c);
        return c;
    }
    public CustomerResponseDTO toResponseDTO(Customer c) {
        if (c == null) return null;
        return new CustomerResponseDTO(
                c.getCustomerNumber(),
                c.getCustomerName(),
                c.getContactLastName(),
                c.getContactFirstName(),
                c.getPhone(),
                c.getAddressLine1(),
                c.getAddressLine2(),
                c.getCity(),
                c.getState(),
                c.getPostalCode(),
                c.getCountry(),
                c.getSalesRepEmployeeNumber(),
                c.getCreditLimit()
        );
    }
    public void copyToEntity(CustomerRequestDTO dto, Customer entity) {
        if (dto == null || entity == null) return;
        entity.setCustomerName(dto.customerName());
        entity.setContactLastName(dto.contactLastName());
        entity.setContactFirstName(dto.contactFirstName());
        entity.setPhone(dto.phone());
        entity.setAddressLine1(dto.addressLine1());
        entity.setAddressLine2(dto.addressLine2());
        entity.setCity(dto.city());
        entity.setState(dto.state());
        entity.setPostalCode(dto.postalCode());
        entity.setCountry(dto.country());
        entity.setSalesRepEmployeeNumber(dto.salesRepEmployeeNumber());
        entity.setCreditLimit(dto.creditLimit());
    }
} */

