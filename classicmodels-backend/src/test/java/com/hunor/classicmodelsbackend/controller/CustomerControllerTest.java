// package com.hunor.classicmodelsbackend.controller;
// 
// import com.fasterxml.jackson.databind.ObjectMapper;
// 
// import java.util.Collections;
// import java.util.List;
// import java.util.Arrays;
// import java.math.BigDecimal;
// 
// import com.hunor.classicmodelsbackend.dto.customer.CustomerRequestDTO;
// import com.hunor.classicmodelsbackend.dto.customer.CustomerResponseDTO;
// import com.hunor.classicmodelsbackend.response.PageResponse;
// import com.hunor.classicmodelsbackend.service.CustomerService;
// import org.junit.jupiter.api.Test;
// import org.springframework.beans.factory.annotation.Autowired;
// import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
// import org.springframework.http.MediaType;
// import org.springframework.test.web.servlet.MockMvc;
// import org.springframework.web.server.ResponseStatusException;
// import org.springframework.http.HttpStatus;
// import org.springframework.test.context.bean.override.mockito.MockitoBean;
// import static org.mockito.ArgumentMatchers.any;
// import static org.mockito.ArgumentMatchers.eq;
// import static org.mockito.Mockito.*;
// import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
// import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
// import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
// import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
// 
// @WebMvcTest(CustomerController.class)
// class CustomerControllerTest {
// 
//     @Autowired
//     private MockMvc mockMvc;
// 
//     @Autowired
//     private ObjectMapper objectMapper;
// 
//     @MockitoBean
//     private CustomerService customerService;
// 
//     @Test
//     void findAll_ShouldReturnListOfCustomers_WhenCustomersExist() throws Exception {
//         // Arrange
//         // Note: Adjust the constructor arguments below to match your actual CustomerResponseDTO structure.
//         // The fields are based on the classicmodels DB schema.
//         CustomerResponseDTO customer1 = new CustomerResponseDTO(
//                 101, "Atelier graphique", "Schmitt", "Carine", "40.32.2555",
//                 "54, rue Royale", null, "Nantes", null, "44000", "France", 1370, new BigDecimal("21000.00")
//         );
//         CustomerResponseDTO customer2 = new CustomerResponseDTO(
//                 103, "Signal Gift Stores", "King", "Jean", "7025551838",
//                 "8489 Strong St.", null, "Las Vegas", "NV", "83030", "USA", 1166, new BigDecimal("71800.00")
//         );
//         
//         List<CustomerResponseDTO> mockCustomers = Arrays.asList(customer1, customer2);
//         when(customerService.findAll()).thenReturn(mockCustomers);
// 
//         // Act & Assert
//         mockMvc.perform(get("/customers")
//                 .accept(MediaType.APPLICATION_JSON))
//                 .andExpect(status().isOk())
//                 .andExpect(jsonPath("$.length()").value(2))
//                 .andExpect(jsonPath("$[0].customerNumber").value(101))
//                 .andExpect(jsonPath("$[0].customerName").value("Atelier graphique"))
//                 .andExpect(jsonPath("$[1].customerNumber").value(103))
//                 .andExpect(jsonPath("$[1].customerName").value("Signal Gift Stores"));
//                 
//         verify(customerService).findAll();
//     }
// 
//     @Test
//     void findAll_ShouldReturnEmptyList_WhenNoCustomersExist() throws Exception {
//         // Arrange
//         when(customerService.findAll()).thenReturn(Collections.emptyList());
// 
//         // Act & Assert
//         mockMvc.perform(get("/customers").accept(MediaType.APPLICATION_JSON))
//                 .andExpect(status().isOk())
//                 .andExpect(jsonPath("$.length()").value(0));
//     }
// 
//     @Test
//     void findById_ShouldReturnCustomer_WhenCustomerExists() throws Exception {
//         // Arrange
//         int customerId = 101;
//         CustomerResponseDTO mockCustomer = new CustomerResponseDTO(
//                 customerId, "Atelier graphique", "Schmitt", "Carine", "40.32.2555",
//                 "54, rue Royale", null, "Nantes", null, "44000", "France", 1370, new BigDecimal("21000.00")
//         );
//         when(customerService.findById(customerId)).thenReturn(mockCustomer);
// 
//         // Act & Assert
//         mockMvc.perform(get("/customers/{id}", customerId)
//                 .accept(MediaType.APPLICATION_JSON))
//                 .andExpect(status().isOk())
//                 .andExpect(jsonPath("$.customerNumber").value(101))
//                 .andExpect(jsonPath("$.customerName").value("Atelier graphique"));
//                 
//         verify(customerService).findById(customerId);
//     }
// 
//     @Test
//     void findById_ShouldReturn404_WhenCustomerDoesNotExist() throws Exception {
//         // Arrange
//         int customerId = 999;
//         // Assuming your service throws an exception that maps to 404 (e.g. EntityNotFoundException, or ResponseStatusException)
//         when(customerService.findById(customerId))
//                 .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found"));
// 
//         // Act & Assert
//         mockMvc.perform(get("/customers/{id}", customerId)
//                 .accept(MediaType.APPLICATION_JSON))
//                 .andExpect(status().isNotFound());
//                 
//         verify(customerService).findById(customerId);
//     }
// 
//     @Test
//     void create_ShouldReturn201AndCreatedCustomer_WhenInputIsValid() throws Exception {
//         // Arrange
//         CustomerRequestDTO requestDTO = new CustomerRequestDTO(
//                 "Atelier graphique", "Schmitt", "Carine", "40.32.2555",
//                 "54, rue Royale", null, "Nantes", null, "44000", "France", 1370, new BigDecimal("21000.00")
//         );
//         
//         CustomerResponseDTO responseDTO = new CustomerResponseDTO(
//                 101, "Atelier graphique", "Schmitt", "Carine", "40.32.2555",
//                 "54, rue Royale", null, "Nantes", null, "44000", "France", 1370, new BigDecimal("21000.00")
//         );
// 
//         when(customerService.create(any(CustomerRequestDTO.class))).thenReturn(responseDTO);
// 
//         // Act & Assert
//         mockMvc.perform(post("/customers")
//                 .contentType(MediaType.APPLICATION_JSON)
//                 .content(objectMapper.writeValueAsString(requestDTO))
//                 .accept(MediaType.APPLICATION_JSON))
//                 .andExpect(status().isCreated())
//                 .andExpect(header().string("Location", "/api/customers/101"))
//                 .andExpect(jsonPath("$.customerNumber").value(101))
//                 .andExpect(jsonPath("$.customerName").value("Atelier graphique"));
//                 
//         verify(customerService).create(any(CustomerRequestDTO.class));
//     }
// 
//     @Test
//     void update_ShouldReturn200AndUpdatedCustomer_WhenInputIsValid() throws Exception {
//         // Arrange
//         int customerId = 101;
//         CustomerRequestDTO requestDTO = new CustomerRequestDTO(
//                 "Atelier graphique Updated", "Schmitt", "Carine", "40.32.2555",
//                 "54, rue Royale", null, "Nantes", null, "44000", "France", 1370, new BigDecimal("21000.00")
//         );
//         
//         CustomerResponseDTO responseDTO = new CustomerResponseDTO(
//                 customerId, "Atelier graphique Updated", "Schmitt", "Carine", "40.32.2555",
//                 "54, rue Royale", null, "Nantes", null, "44000", "France", 1370, new BigDecimal("21000.00")
//         );
// 
//         // We use eq(customerId) to ensure the mock only triggers for this specific ID
//         when(customerService.update(eq(customerId), any(CustomerRequestDTO.class))).thenReturn(responseDTO);
// 
//         // Act & Assert
//         mockMvc.perform(put("/customers/{id}", customerId)
//                 .contentType(MediaType.APPLICATION_JSON)
//                 .content(objectMapper.writeValueAsString(requestDTO))
//                 .accept(MediaType.APPLICATION_JSON))
//                 .andExpect(status().isOk())
//                 .andExpect(jsonPath("$.customerNumber").value(customerId))
//                 .andExpect(jsonPath("$.customerName").value("Atelier graphique Updated"));
//                 
//         verify(customerService).update(eq(customerId), any(CustomerRequestDTO.class));
//     }
// 
//     @Test
//     void update_ShouldReturn404_WhenCustomerDoesNotExist() throws Exception {
//         // Arrange
//         int customerId = 999;
//         CustomerRequestDTO requestDTO = new CustomerRequestDTO(
//                 "Non-existent", "Schmitt", "Carine", "40.32.2555",
//                 "54, rue Royale", null, "Nantes", null, "44000", "France", 1370, new BigDecimal("21000.00")
//         );
// 
//         when(customerService.update(eq(customerId), any(CustomerRequestDTO.class)))
//                 .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found"));
// 
//         // Act & Assert
//         mockMvc.perform(put("/customers/{id}", customerId)
//                 .contentType(MediaType.APPLICATION_JSON)
//                 .content(objectMapper.writeValueAsString(requestDTO))
//                 .accept(MediaType.APPLICATION_JSON))
//                 .andExpect(status().isNotFound());
//                 
//         verify(customerService).update(eq(customerId), any(CustomerRequestDTO.class));
//     }
// 
//     @Test
//     void delete_ShouldReturn204_WhenCustomerIsDeletedSuccessfully() throws Exception {
//         // Arrange
//         int customerId = 101;
//         
//         // When a method returns void, we use doNothing() in Mockito
//         doNothing().when(customerService).delete(customerId);
// 
//         // Act & Assert
//         mockMvc.perform(delete("/customers/{id}", customerId))
//                 .andExpect(status().isNoContent());
//                 
//         verify(customerService).delete(customerId);
//     }
// 
//     @Test
//     void delete_ShouldReturn404_WhenCustomerDoesNotExist() throws Exception {
//         // Arrange
//         int customerId = 999;
//         
//         doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found"))
//                 .when(customerService).delete(customerId);
// 
//         // Act & Assert
//         mockMvc.perform(delete("/customers/{id}", customerId))
//                 .andExpect(status().isNotFound());
//                 
//         verify(customerService).delete(customerId);
//     }
// 
//     @Test
//     void findAllPaged_ShouldReturnPagedCustomers_WhenDefaultParamsUsed() throws Exception {
//         // Arrange
//         CustomerResponseDTO customer1 = new CustomerResponseDTO(
//                 101, "Atelier graphique", "Schmitt", "Carine", "40.32.2555",
//                 "54, rue Royale", null, "Nantes", null, "44000", "France", 1370, new BigDecimal("21000.00")
//         );
//         List<CustomerResponseDTO> content = List.of(customer1);
// 
//         PageResponse<CustomerResponseDTO> pageResponse = new PageResponse<>(
//                 content, 0, 20, 1L, 1
//         );
// 
//         when(customerService.findAllPaged(0, 20, "customerName", true)).thenReturn(pageResponse);
// 
//         // Act & Assert
//         mockMvc.perform(get("/customers/find-all-paged")
//                 .accept(MediaType.APPLICATION_JSON))
//                 .andExpect(status().isOk())
//                 .andExpect(jsonPath("$.content.length()").value(1))
//                 .andExpect(jsonPath("$.content[0].customerNumber").value(101))
//                 .andExpect(jsonPath("$.content[0].customerName").value("Atelier graphique"));
//                 
//         verify(customerService).findAllPaged(0, 20, "customerName", true);
//     }
// 
//     @Test
//     void findAllPaged_ShouldReturnPagedCustomers_WhenCustomParamsUsed() throws Exception {
//         // Arrange
//         CustomerResponseDTO customer2 = new CustomerResponseDTO(
//                 103, "Signal Gift Stores", "King", "Jean", "7025551838",
//                 "8489 Strong St.", null, "Las Vegas", "NV", "83030", "USA", 1166, new BigDecimal("71800.00")
//         );
//         List<CustomerResponseDTO> content = List.of(customer2);
//         
//         PageResponse<CustomerResponseDTO> pageResponse = new PageResponse<>(
//                 content, 1, 10, 25L, 3
//         );
// 
//         when(customerService.findAllPaged(1, 10, "contactLastName", false)).thenReturn(pageResponse);
// 
//         // Act & Assert
//         mockMvc.perform(get("/customers/find-all-paged")
//                 .param("page", "1")
//                 .param("size", "10")
//                 .param("sort", "contactLastName")
//                 .param("dir", "desc")
//                 .accept(MediaType.APPLICATION_JSON))
//                 .andExpect(status().isOk())
//                 .andExpect(jsonPath("$.content.length()").value(1))
//                 .andExpect(jsonPath("$.content[0].customerNumber").value(103))
//                 .andExpect(jsonPath("$.content[0].customerName").value("Signal Gift Stores"));
//                 
//         verify(customerService).findAllPaged(1, 10, "contactLastName", false);
//     }
// 
//     @Test
//     void createBulk_ShouldReturn202_WhenInputIsValid() throws Exception {
//         // Arrange
//         CustomerRequestDTO request1 = new CustomerRequestDTO(
//                 "Atelier graphique", "Schmitt", "Carine", "40.32.2555",
//                 "54, rue Royale", null, "Nantes", null, "44000", "France", 1370, new BigDecimal("21000.00")
//         );
//         CustomerRequestDTO request2 = new CustomerRequestDTO(
//                 "Signal Gift Stores", "King", "Jean", "7025551838",
//                 "8489 Strong St.", null, "Las Vegas", "NV", "83030", "USA", 1166, new BigDecimal("71800.00")
//         );
//         List<CustomerRequestDTO> requestList = Arrays.asList(request1, request2);
// 
//         doNothing().when(customerService).createBulk(any());
// 
//         // Act & Assert
//         mockMvc.perform(post("/customers/bulk")
//                 .contentType(MediaType.APPLICATION_JSON)
//                 .content(objectMapper.writeValueAsString(requestList)))
//                 .andExpect(status().isAccepted());
//                 
//         verify(customerService).createBulk(any());
//     }
// }