package com.hunor.classicmodelsbackend.mapper;

import com.hunor.classicmodelsbackend.dto.office.OfficeRequestDTO;
import com.hunor.classicmodelsbackend.dto.office.OfficeResponseDTO;
import com.hunor.classicmodelsbackend.model.Office;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface OfficeMapper {
    @Mapping(target = "officeCode", ignore = true)
    Office toEntity(OfficeRequestDTO dto);

    OfficeResponseDTO toResponseDTO(Office c);

    void copyToEntity(OfficeRequestDTO dto, @MappingTarget Office entity);
}

/* @Component
public class OfficeMapper {
    public Office toEntity(OfficeRequestDTO dto) {
        if (dto == null) return null;
        var o = new Office();
        copyToEntity(dto, o);
        return o;
    }
    public OfficeResponseDTO toResponseDTO(Office o) {
        if (o == null) return null;
        return new OfficeResponseDTO(
                o.getOfficeCode(),
                o.getCity(),
                o.getPhone(),
                o.getAddressLine1(),
                o.getAddressLine2(),
                o.getState(),
                o.getCountry(),
                o.getPostalCode(),
                o.getTerritory()
        );
    }
    public void copyToEntity(OfficeRequestDTO dto, Office target) {
        if (dto == null || target == null) return;
        target.setCity(dto.city());
        target.setPhone(dto.phone());
        target.setAddressLine1(dto.addressLine1());
        target.setAddressLine2(dto.addressLine2());
        target.setState(dto.state());
        target.setCountry(dto.country());
        target.setPostalCode(dto.postalCode());
        target.setTerritory(dto.territory());
    }
} */


