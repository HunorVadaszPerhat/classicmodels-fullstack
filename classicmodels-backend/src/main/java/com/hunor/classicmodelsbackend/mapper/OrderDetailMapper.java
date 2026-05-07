package com.hunor.classicmodelsbackend.mapper;

import com.hunor.classicmodelsbackend.dto.orderdetail.OrderDetailRequestDTO;
import com.hunor.classicmodelsbackend.dto.orderdetail.OrderDetailResponseDTO;
import com.hunor.classicmodelsbackend.model.OrderDetail;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;


@Mapper(componentModel = "spring")
public interface OrderDetailMapper {
    @Mapping(target = "orderNumber", ignore = true)
    @Mapping(target = "productCode", ignore = true)
    OrderDetail toEntity(OrderDetailRequestDTO dto);

    OrderDetailResponseDTO toResponseDTO(OrderDetail od);

    void copyToEntity(OrderDetailRequestDTO dto, @MappingTarget OrderDetail target);
}

/* @Component
public class OrderDetailMapper {
    public OrderDetail toEntity(OrderDetailRequestDTO dto) {
        if (dto == null) return null;
        var od = new OrderDetail();
        od.setOrderNumber(dto.orderNumber());
        od.setProductCode(dto.productCode());
        copyToEntity(dto, od);
        return od;
    }
    public OrderDetailResponseDTO toResponseDTO(OrderDetail od) {
        if (od == null) return null;
        return new OrderDetailResponseDTO(
                od.getOrderNumber(),
                od.getProductCode(),
                od.getQuantityOrdered(),
                od.getPriceEach(),
                od.getOrderLineNumber()
        );
    }
    public void copyToEntity(OrderDetailRequestDTO dto, OrderDetail target) {
        if (dto == null || target == null) return;
        target.setQuantityOrdered(dto.quantityOrdered());
        target.setPriceEach(dto.priceEach());
        target.setOrderLineNumber(dto.orderLineNumber());
    }
} */


