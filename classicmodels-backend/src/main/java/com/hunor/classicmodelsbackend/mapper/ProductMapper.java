package com.hunor.classicmodelsbackend.mapper;

import com.hunor.classicmodelsbackend.dto.product.ProductRequestDTO;
import com.hunor.classicmodelsbackend.dto.product.ProductResponseDTO;
import com.hunor.classicmodelsbackend.model.Product;

import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ProductMapper {
    @Mapping(target = "productCode", ignore = true)
    Product toEntity(ProductRequestDTO dto);

    ProductResponseDTO toResponseDTO(Product p);

    void copyToEntity(ProductRequestDTO dto, @MappingTarget Product entity);
}

/* 
@Component
public class ProductMapper {
    public Product toEntity(ProductRequestDTO dto) {
        if (dto == null) return null;
        var p = new Product();
        p.setProductCode(dto.productCode());
        copyToEntity(dto, p);
        return p;
    }
    public ProductResponseDTO toResponseDTO(Product p) {
        if (p == null) return null;
        return new ProductResponseDTO(
                p.getProductCode(),
                p.getProductName(),
                p.getProductLine(),
                p.getProductScale(),
                p.getProductVendor(),
                p.getProductDescription(),
                p.getQuantityInStock(),
                p.getBuyPrice(),
                p.getMsrp()
        );
    }
    public void copyToEntity(ProductRequestDTO dto, Product target) {
        if (dto == null || target == null) return;
        target.setProductName(dto.productName());
        target.setProductLine(dto.productLine());
        target.setProductScale(dto.productScale());
        target.setProductVendor(dto.productVendor());
        target.setProductDescription(dto.productDescription());
        target.setQuantityInStock(dto.quantityInStock());
        target.setBuyPrice(dto.buyPrice());
        target.setMsrp(dto.msrp());
    }
}
 */