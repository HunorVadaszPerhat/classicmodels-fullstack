package com.hunor.classicmodelsbackend.mapper;

import com.hunor.classicmodelsbackend.dto.order.OrderRequestDTO;
import com.hunor.classicmodelsbackend.dto.order.OrderResponseDTO;
import com.hunor.classicmodelsbackend.model.Order;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface OrderMapper {
    @Mapping(target = "orderNumber", ignore = true)
    Order toEntity(OrderRequestDTO dto);

    OrderResponseDTO toResponseDTO(Order o);

    void copyToEntity(OrderRequestDTO dto, @MappingTarget Order target);
}

/* @Component
public class OrderMapper {
    public Order toEntity(OrderRequestDTO dto) {
        if (dto == null) return null;
        var o = new Order();
        copyToEntity(dto, o);
        return o;
    }
    public OrderResponseDTO toResponseDTO(Order o) {
        if (o == null) return null;
        return new OrderResponseDTO(
                o.getOrderNumber(),
                o.getOrderDate(),
                o.getRequiredDate(),
                o.getShippedDate(),
                o.getStatus(),
                o.getComments(),
                o.getCustomerNumber()
        );
    }
    public void copyToEntity(OrderRequestDTO dto, Order target) {
        if (dto == null || target == null) return;
        target.setOrderDate(dto.orderDate());
        target.setRequiredDate(dto.requiredDate());
        target.setShippedDate(dto.shippedDate());
        target.setStatus(dto.status());
        target.setComments(dto.comments());
        target.setCustomerNumber(dto.customerNumber());
    }
} */


