package com.hunor.classicmodelsbackend.mapper;

import com.hunor.classicmodelsbackend.dto.payment.PaymentRequestDTO;
import com.hunor.classicmodelsbackend.dto.payment.PaymentResponseDTO;
import com.hunor.classicmodelsbackend.model.Payment;

import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PaymentMapper {
    @Mapping(target = "customerNumber", ignore = true)
    Payment toEntity(PaymentRequestDTO dto);

    PaymentResponseDTO toResponseDTO(Payment p);

    void copyToEntity(PaymentRequestDTO dto, @MappingTarget Payment entity);
}

/* @Component
public class PaymentMapper {
    public Payment toEntity(PaymentRequestDTO dto) {
        if (dto == null) return null;
        var p = new Payment();
        p.setCustomerNumber(dto.customerNumber());
        p.setCheckNumber(dto.checkNumber());
        copyToEntity(dto, p);
        return p;
    }
    public PaymentResponseDTO toResponseDTO(Payment p) {
        if (p == null) return null;
        return new PaymentResponseDTO(
                p.getCustomerNumber(),
                p.getCheckNumber(),
                p.getPaymentDate(),
                p.getAmount()
        );
    }

    public void copyToEntity(PaymentRequestDTO dto, Payment target) {
        if (dto == null || target == null) return;
        target.setPaymentDate(dto.paymentDate());
        target.setAmount(dto.amount());
    }
} */
