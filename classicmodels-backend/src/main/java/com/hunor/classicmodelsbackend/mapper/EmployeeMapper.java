package com.hunor.classicmodelsbackend.mapper;

import com.hunor.classicmodelsbackend.dto.employee.EmployeeRequestDTO;
import com.hunor.classicmodelsbackend.dto.employee.EmployeeResponseDTO;
import com.hunor.classicmodelsbackend.model.Employee;

import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface EmployeeMapper {
    @Mapping(target = "employeeNumber", ignore = true)
    Employee toEntity(EmployeeRequestDTO dto);

    EmployeeResponseDTO toResponseDTO(Employee c);

    void copyToEntity(EmployeeRequestDTO dto, @MappingTarget Employee entity);
}

/*@Component
public class EmployeeMapper {
    public Employee toEntity(EmployeeRequestDTO dto) {
        if (dto == null) return null;
        var e = new Employee();
        copyToEntity(dto, e);
        return e;
    }
    public EmployeeResponseDTO toResponseDTO(Employee e) {
        if (e == null) return null;
        return new EmployeeResponseDTO(
                e.getEmployeeNumber(),
                e.getLastName(),
                e.getFirstName(),
                e.getExtension(),
                e.getEmail(),
                e.getOfficeCode(),
                e.getReportsTo(),
                e.getJobTitle()
        );
    }
    public void copyToEntity(EmployeeRequestDTO dto, Employee target) {
        if (dto == null || target == null) return;
        target.setLastName(dto.lastName());
        target.setFirstName(dto.firstName());
        target.setExtension(dto.extension());
        target.setEmail(dto.email());
        target.setOfficeCode(dto.officeCode());
        target.setReportsTo(dto.reportsTo());
        target.setJobTitle(dto.jobTitle());
    }
}*/


