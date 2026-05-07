// package com.hunor.classicmodelsbackend.controller;
// 
// import com.fasterxml.jackson.databind.ObjectMapper;
// import com.hunor.classicmodelsbackend.dto.employee.EmployeeRequestDTO;
// import com.hunor.classicmodelsbackend.dto.employee.EmployeeResponseDTO;
// import com.hunor.classicmodelsbackend.response.PageResponse;
// import com.hunor.classicmodelsbackend.service.EmployeeService;
// import org.junit.jupiter.api.Test;
// import org.springframework.beans.factory.annotation.Autowired;
// import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
// import org.springframework.http.HttpStatus;
// import org.springframework.http.MediaType;
// import org.springframework.test.context.bean.override.mockito.MockitoBean;
// import org.springframework.test.web.servlet.MockMvc;
// import org.springframework.web.server.ResponseStatusException;
// 
// import java.util.Arrays;
// import java.util.Collections;
// import java.util.List;
// 
// import static org.hamcrest.Matchers.nullValue;
// import static org.mockito.ArgumentMatchers.any;
// import static org.mockito.ArgumentMatchers.eq;
// import static org.mockito.Mockito.*;
// import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
// import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
// /*
// @WebMvcTest(EmployeeController.class)
// public class EmployeeControllerTest {
//     @Autowired
//     private MockMvc mockMvc;
// 
//     @Autowired
//     private ObjectMapper objectMapper;
// 
//     @MockitoBean
//     private EmployeeService employeeService;
// 
// //    @Test
// //    void findAll_ShouldReturnListOfEmoloyees_WhenCustomerExist() throws Exception {
// //        // Example 1: Diane Murphy (The President - reportsTo is null)
// //        EmployeeResponseDTO employee1 = new EmployeeResponseDTO(
// //                1002,
// //                "Murphy",
// //                "Diane",
// //                "x5800",
// //                "dmurphy@classicmodelcars.com",
// //                "1",     // officeCode (San Francisco)
// //                null,    // reportsTo (President has no manager)
// //                "President"
// //        );
// //
// //        // Example 2: Gerard Hernandez (Sales Rep - reports to 1102)
// //        EmployeeResponseDTO employee2 = new EmployeeResponseDTO(
// //                1370,
// //                "Hernandez",
// //                "Gerard",
// //                "x2028",
// //                "ghernande@classicmodelcars.com",
// //                "4",     // officeCode (Paris)
// //                1102,    // reportsTo (Bondur, Gerard)
// //                "Sales Rep"
// //        );
// //
// //        List<EmployeeResponseDTO> mockEmployees = Arrays.asList(employee1, employee2);
// //        when(employeeService.findAll()).thenReturn(mockEmployees);
// //
// //        // Act and assert
// //        mockMvc.perform(get("/employees")
// //                .accept(MediaType.APPLICATION_JSON))
// //                .andExpect(status().isOk())
// //                .andExpect(jsonPath("$.length()").value(2))
// //                .andExpect(jsonPath("$[0].employeeNumber").value(1002))
// //                .andExpect(jsonPath("$[0].lastName").value("Murphy"))
// //                .andExpect(jsonPath("$[0].firstName").value("Diane"))
// //                .andExpect(jsonPath("$[0].extension").value("x5800"))
// //                .andExpect(jsonPath("$[0].email").value("dmurphy@classicmodelcars.com"))
// //                .andExpect(jsonPath("$[0].officeCode").value("1"))
// //                .andExpect(jsonPath("$[0].reportsTo").value(nullValue()))
// //                .andExpect(jsonPath("$[0].jobTitle").value("President"))
// //
// //                .andExpect(jsonPath("$[1].employeeNumber").value(1370))
// //                .andExpect(jsonPath("$[1].lastName").value("Hernandez"))
// //                .andExpect(jsonPath("$[1].firstName").value("Gerard"))
// //                .andExpect(jsonPath("$[1].extension").value("x2028"))
// //                .andExpect(jsonPath("$[1].email").value("ghernande@classicmodelcars.com"))
// //                .andExpect(jsonPath("$[1].officeCode").value("4"))
// //                .andExpect(jsonPath("$[1].reportsTo").value(1102))
// //                .andExpect(jsonPath("$[1].jobTitle").value("Sales Rep"));
// //
// //        verify(employeeService).findAll();
// //    }
// 
//     @Test
//     void findAll_ShouldReturnEmptyList_WhenNoCustomersExist() throws Exception {
//         // Arrange
//         when(employeeService.findAll()).thenReturn(Collections.emptyList());
// 
//         // Act & Assert
//         mockMvc.perform(get("/employees").accept(MediaType.APPLICATION_JSON))
//                 .andExpect(status().isOk())
//                 .andExpect(jsonPath("$.length()").value(0));
//     }
// 
//     @Test
//     void findById_ShouldReturnEmployee_WhenEmployeeExists() throws Exception {
//         // arrange
//         int employeeNumber = 1370;
// 
//         // Gerard Hernandez (Sales Rep - reports to 1102)
//         EmployeeResponseDTO mockEmployee = new EmployeeResponseDTO(
//                 employeeNumber,
//                 "Hernandez",
//                 "Gerard",
//                 "x2028",
//                 "ghernande@classicmodelcars.com",
//                 "4",     // officeCode (Paris)
//                 1102,    // reportsTo (Bondur, Gerard)
//                 "Sales Rep"
//         );
// 
//         when(employeeService.findById(employeeNumber)).thenReturn(mockEmployee);
// 
//         // Act and Assert
//         mockMvc.perform(get("/employees/{id}", employeeNumber)
//                 .accept(MediaType.APPLICATION_JSON))
//                 .andExpect(status().isOk())
//                 .andExpect(jsonPath("$.employeeNumber").value(1370))
//                 .andExpect(jsonPath("$.firstName").value("Gerard"))
//                 .andExpect(jsonPath("$.lastName").value("Hernandez"))
//                 .andExpect(jsonPath("$.extension").value("x2028"))
//                 .andExpect(jsonPath("$.email").value("ghernande@classicmodelcars.com"))
//                 .andExpect(jsonPath("$.officeCode").value("4"))
//                 .andExpect(jsonPath("$.reportsTo").value(1102))
//                 .andExpect(jsonPath("$.jobTitle").value("Sales Rep"));
// 
//             verify(employeeService).findById(employeeNumber);
//     }
// 
//     @Test
//     void findById_ShouldReturn404_WhenEmployeeDoesNotExist() throws Exception {
//         // Arrange
//         int employeeNumber = 1370;
// 
//         // assuming your service throws an exception that maps to 404
//         when(employeeService.findById(employeeNumber))
//                 .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee not found"));
// 
//         // act and assert
//         mockMvc.perform(get("/employees/{id}", employeeNumber)
//                 .accept(MediaType.APPLICATION_JSON))
//                 .andExpect(status().isNotFound());
// 
//         verify(employeeService).findById(employeeNumber);
//     }
// 
//     @Test
//     void create_ShouldReturn201AndCreatedEmployee_WhenInputIsValid() throws Exception {
//         // arrange
//         EmployeeRequestDTO requestDTO = new EmployeeRequestDTO(
//                 "Murphy",
//                 "Diane",
//                 "x5800",
//                 "dmurphy@classicmodelcars.com",
//                 "1",     // officeCode (San Francisco)
//                 null,    // reportsTo (President has no manager)
//                 "President"
//         );
// 
//         EmployeeResponseDTO responseDTO = new EmployeeResponseDTO(
//                 1002,
//                 "Murphy",
//                 "Diane",
//                 "x5800",
//                 "dmurphy@classicmodelcars.com",
//                 "1",     // officeCode (San Francisco)
//                 null,    // reportsTo (President has no manager)
//                 "President"
//         );
// 
//         when(employeeService.create(any(EmployeeRequestDTO.class))).thenReturn(responseDTO);
// 
//         // act and assert
//         mockMvc.perform(post("/employees")
//                 .contentType(MediaType.APPLICATION_JSON)
//                 .content(objectMapper.writeValueAsString(requestDTO))
//                 .accept(MediaType.APPLICATION_JSON))
//                 .andExpect(status().isCreated())
//                 .andExpect(header().string("Location", "/api/employees/1002"))
//                 .andExpect(jsonPath("$.employeeNumber").value(1002))
//                 .andExpect(jsonPath("$.lastName").value("Murphy"))
//                 .andExpect(jsonPath("$.firstName").value("Diane")
//         );
// 
//         verify(employeeService).create(any(EmployeeRequestDTO.class));
//     }
// 
//     @Test
//     void update_ShouldReturn200AndUpdatedEmployee_WhenInputIsValid() throws Exception {
//         // arrange
//         int employeeNumber = 1002;
//         EmployeeRequestDTO requestDTO = new EmployeeRequestDTO(
//                 "Murphy",
//                 "Diane",
//                 "x5800",
//                 "dmurphy@classicmodelcars.com",
//                 "1",     // officeCode (San Francisco)
//                 null,    // reportsTo (President has no manager)
//                 "President"
//         );
// 
//         EmployeeResponseDTO responseDTO = new EmployeeResponseDTO(
//                 employeeNumber,
//                 "Murphy Updated",
//                 "Diane",
//                 "x5800",
//                 "dmurphy@classicmodelcars.com",
//                 "1",     // officeCode (San Francisco)
//                 null,    // reportsTo (President has no manager)
//                 "President"
//         );
// 
//         // we use eq(employeeNumber) to ensure the mock only triggers for this specific ID
//         when(employeeService.update(eq(employeeNumber), any(EmployeeRequestDTO.class))).thenReturn(responseDTO);
// 
//         // act & assert
//         mockMvc.perform(put("/employees/{d}", employeeNumber)
//                 .contentType(MediaType.APPLICATION_JSON)
//                 .content(objectMapper.writeValueAsString(requestDTO))
//                 .accept(MediaType.APPLICATION_JSON))
//                 .andExpect(status().isOk())
//                 .andExpect(jsonPath("$.employeeNumber").value(employeeNumber))
//                 .andExpect(jsonPath("$.lastName").value("Murphy Updated"))
//                 .andExpect(jsonPath("$.firstName").value("Diane"))
//                 .andExpect(jsonPath("$.extension").value("x5800"))
//                 .andExpect(jsonPath("$.email").value("dmurphy@classicmodelcars.com"))
//                 .andExpect(jsonPath("$.officeCode").value("1"))
//                 .andExpect(jsonPath("$.reportsTo").value(nullValue()))
//                 .andExpect(jsonPath("$.jobTitle").value("President")
//         );
// 
//         verify(employeeService).update(eq(employeeNumber), any(EmployeeRequestDTO.class));
//     }
// 
//     @Test
//     void udpate_ShouldReturn404_WhenEmployeeDoesNotExist() throws Exception {
//         // arrange
//         int employeeNumber = 999;
//         EmployeeRequestDTO requestDTO = new EmployeeRequestDTO(
//                 "Non",
//                 "Existent",
//                 "x5800",
//                 "non-existent@classicmodelcars.com",
//                 "1",     // officeCode (San Francisco)
//                 null,    // reportsTo (President has no manager)
//                 "President"
//         );
// 
//         when(employeeService.update(eq(employeeNumber), any(EmployeeRequestDTO.class)))
//                 .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee not found"));
// 
//         // act and assert
//         mockMvc.perform(put("/employees/{id}", employeeNumber)
//                 .contentType(MediaType.APPLICATION_JSON)
//                 .content(objectMapper.writeValueAsString(requestDTO))
//                 .accept(MediaType.APPLICATION_JSON))
//                 .andExpect(status().isNotFound()
//         );
// 
//         verify(employeeService).update(eq(employeeNumber), any(EmployeeRequestDTO.class));
//     }
// 
//     @Test
//     void delete_ShouldReturn204_WhenEmployeeIsDeletedSuccessFully() throws Exception {
//         // arrange
//         int employeeNumber = 1002;
// 
//         // when a method returns void, we use doNothing() in Mockito
//         doNothing().when(employeeService).delete(employeeNumber);
// 
//         // act and assert
//         mockMvc.perform(delete("/employees/{id}", employeeNumber)).andExpect(status().isNoContent());
// 
//         verify(employeeService).delete(employeeNumber);
//     }
// 
//     @Test
//     void delete_ShouldReturn404_WhenEmployeeDoesNotExist() throws Exception {
//         // arrange
//         int employeeNumber = 999;
//         doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee not found"))
//                 .when(employeeService).delete(employeeNumber);
// 
//         // act and assert
//         mockMvc.perform(delete("/employees/{id}", employeeNumber))
//                 .andExpect(status().isNotFound());
// 
//         verify(employeeService).delete(employeeNumber);
//     }
// 
//     @Test
//     void findAllPaged_ShouldReturnPagedEmployees_WhenDefaultParamsUsed() throws Exception {
//         // arrange
//         EmployeeResponseDTO employee1 = new EmployeeResponseDTO(
//                 1002,
//                 "Murphy",
//                 "Diane",
//                 "x5800",
//                 "dmurphy@classicmodelcars.com",
//                 "1",     // officeCode (San Francisco)
//                 null,    // reportsTo (President has no manager)
//                 "President"
//         );
// 
//         List<EmployeeResponseDTO> content = List.of(employee1);
//         PageResponse<EmployeeResponseDTO> pageResponse = new PageResponse<>(content, 0, 20, 1L, 1);
// 
//         when(employeeService.findAllPaged(0, 20, "lastName", true)).thenReturn(pageResponse);
// 
//         // act and assert
//         mockMvc.perform(get("/employees/find-all-paged")
//                 .accept(MediaType.APPLICATION_JSON))
//                 .andExpect(status().isOk())
//                 .andExpect(jsonPath("$.content.length()").value(1))
//                 .andExpect(jsonPath("$.content[0].employeeNumber").value(1002))
//                 .andExpect(jsonPath("$.content[0].lastName").value("Murphy")
//         );
// 
//         verify(employeeService).findAllPaged(0, 20, "lastName", true);
//     }
// 
//     @Test
//     void findAllPaged_ShouldReturnPagedEmployees_WhenEmployeeParamsUsed() throws Exception {
//         // arrange
//         EmployeeResponseDTO employee1 = new EmployeeResponseDTO(
//                 1002,
//                 "Murphy",
//                 "Diane",
//                 "x5800",
//                 "dmurphy@classicmodelcars.com",
//                 "1",     // officeCode (San Francisco)
//                 null,    // reportsTo (President has no manager)
//                 "President"
//         );
// 
//         List<EmployeeResponseDTO> content = List.of(employee1);
//         PageResponse<EmployeeResponseDTO> pageResponse = new PageResponse<>(content, 1, 20, 25L, 3);
// 
//         when(employeeService.findAllPaged(1, 10, "lastName", false)).thenReturn(pageResponse);
// 
//         // act and assert
//         mockMvc.perform(get("/employees/find-all-paged")
//                 .param("page", "1")
//                 .param("size", "10")
//                 .param("sort", "lastName")
//                 .param("dir", "desc")
//                 .accept(MediaType.APPLICATION_JSON))
//                 .andExpect(status().isOk())
//                 .andExpect(jsonPath("$.content.length()").value(1))
//                 .andExpect(jsonPath("$.content[0].employeeNumber").value(1002))
//                 .andExpect(jsonPath("$.content[0].lastName").value("Murphy")
//         );
//         verify(employeeService).findAllPaged(1,10, "lastName", false);
//     }
// 
//     @Test
//     void createBulk_ShouldReturn202_WhenInputIsValid() throws Exception {
//         // arrange
//         EmployeeRequestDTO request1 = new EmployeeRequestDTO(
//                 "Murphy",
//                 "Diane",
//                 "x5800",
//                 "dmurphy@classicmodelcars.com",
//                 "1",     // officeCode
//                 null,    // reportsTo
//                 "President"
//         );
//         EmployeeRequestDTO request2 = new EmployeeRequestDTO(
//                 "Hernandez",
//                 "Gerard",
//                 "x2028",
//                 "ghernande@classicmodelcars.com",
//                 "4",     // officeCode
//                 1102,    // reportsTo
//                 "Sales Rep"
//         );
// 
//         List<EmployeeRequestDTO> requestList = Arrays.asList(request1, request2);
// 
//         doNothing().when(employeeService).createBulk(any());
// 
//         // act and assert
//         mockMvc.perform(
//                 post("/employees/bulk")
//                         .contentType(MediaType.APPLICATION_JSON)
//                         .content(objectMapper.writeValueAsString(requestList))
//                 )
//                 .andExpect(status().isAccepted()
//         );
// 
//         verify(employeeService).createBulk(any());
//     }
// }
//  */
