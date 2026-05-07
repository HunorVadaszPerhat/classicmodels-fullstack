// package com.hunor.classicmodelsbackend.service;
// 
// import java.util.List;
// import java.util.Optional;
// 
// import static org.junit.jupiter.api.Assertions.assertEquals;
// import static org.junit.jupiter.api.Assertions.assertNotNull;
// import static org.junit.jupiter.api.Assertions.assertThrows;
// import org.junit.jupiter.api.Test;
// import org.junit.jupiter.api.extension.ExtendWith;
// import static org.mockito.ArgumentMatchers.any;
// import static org.mockito.ArgumentMatchers.anyInt;
// import org.mockito.InjectMocks;
// import org.mockito.Mock;
// import static org.mockito.Mockito.doNothing;
// import static org.mockito.Mockito.mock;
// import static org.mockito.Mockito.never;
// import static org.mockito.Mockito.times;
// import static org.mockito.Mockito.verify;
// import static org.mockito.Mockito.when;
// import org.mockito.junit.jupiter.MockitoExtension;
// 
// import com.hunor.classicmodelsbackend.dto.customer.CustomerRequestDTO;
// import com.hunor.classicmodelsbackend.dto.customer.CustomerResponseDTO;
// import com.hunor.classicmodelsbackend.mapper.CustomerMapper;
// import com.hunor.classicmodelsbackend.model.Customer;
// import com.hunor.classicmodelsbackend.model.Employee;
// import com.hunor.classicmodelsbackend.repository.CustomerRepository;
// import com.hunor.classicmodelsbackend.repository.EmployeeRepository;
// import com.hunor.classicmodelsbackend.response.PageResponse;
// 
// /*
// unit tests
// - a fast and completely isolated unit test
// - it does not load the spring application context
// - it verifies the business logic inside CustomerService
// - it does not spin up spring or database so these tests run in milliseconds
// */
// @ExtendWith(MockitoExtension.class)
// class CustomerServiceTest {
// 
//     @Mock
//     private CustomerRepository repo;
// 
//     @Mock
//     private CustomerMapper mapper;
// 
//     @Mock
//     private EmployeeRepository employeeRepo;
// 
//     @InjectMocks
//     private CustomerService service;
// 
//     @Test
//     void findAll_ReturnsListOfCustomerResponseDTO() {
//         // Arrange
//         List<Customer> customers = List.of(new Customer(), new Customer());
//         when(repo.findAll()).thenReturn(customers);
//         when(mapper.toResponseDTO(any(Customer.class))).thenReturn(mock(CustomerResponseDTO.class));
// 
//         // Act
//         List<CustomerResponseDTO> result = service.findAll();
// 
//         // Assert
//         assertEquals(2, result.size());
//         verify(repo).findAll();
//         verify(mapper, times(2)).toResponseDTO(any(Customer.class));
//     }
// 
//     @Test
//     void findById_ReturnsCustomer_WhenFound() {
//         int id = 1;
//         Customer customer = new Customer();
//         when(repo.findById(id)).thenReturn(Optional.of(customer));
//         when(mapper.toResponseDTO(customer)).thenReturn(mock(CustomerResponseDTO.class));
// 
//         CustomerResponseDTO result = service.findById(id);
// 
//         assertNotNull(result);
//         verify(repo).findById(id);
//     }
// 
//     @Test
//     void findById_ThrowsException_WhenNotFound() {
//         int id = 1;
//         when(repo.findById(id)).thenReturn(Optional.empty());
// 
//         RuntimeException exception = assertThrows(RuntimeException.class, () -> service.findById(id));
//         assertEquals("Customer 1 not found", exception.getMessage());
//     }
// 
//     @Test
//     void create_ReturnsCustomer_WhenSalesRepIsNull() {
//         // Arrange
//         CustomerRequestDTO requestDTO = mock(CustomerRequestDTO.class);
//         when(requestDTO.salesRepEmployeeNumber()).thenReturn(null);
// 
//         Customer entity = new Customer();
//         Customer savedEntity = new Customer();
//         CustomerResponseDTO responseDTO = mock(CustomerResponseDTO.class);
// 
//         when(mapper.toEntity(requestDTO)).thenReturn(entity);
//         when(repo.save(entity)).thenReturn(savedEntity);
//         when(mapper.toResponseDTO(savedEntity)).thenReturn(responseDTO);
// 
//         // Act
//         CustomerResponseDTO result = service.create(requestDTO);
// 
//         // Assert
//         assertNotNull(result);
//         assertEquals(responseDTO, result);
//         verify(employeeRepo, never()).findById(anyInt());
//         verify(repo).save(entity);
//     }
// 
//     @Test
//     void create_ReturnsCustomer_WhenSalesRepIsNullTwo() {
//         // arrange
//         CustomerRequestDTO requestDTO = mock(CustomerRequestDTO.class);
//         when(requestDTO.salesRepEmployeeNumber()).thenReturn(null);
// 
//         Customer entity = new Customer();
//         Customer savedEntity = new Customer();
//         CustomerResponseDTO responseDTO = mock(CustomerResponseDTO.class);
// 
//         when(mapper.toEntity(requestDTO)).thenReturn(entity);
//         when(repo.save(entity)).thenReturn(savedEntity);
//         when(mapper.toResponseDTO(savedEntity)).thenReturn(responseDTO);
// 
//         // act
//         CustomerResponseDTO result = service.create(requestDTO);
// 
//         // assert
//         assertNotNull(result);
//         assertEquals(responseDTO, result);
//         verify(employeeRepo, never()).findById(anyInt());
//         verify(repo).save(entity);
//     }
// 
//     @Test
//     void create_ReturnsCustomer_WhenSalesRepIsNotNullAndFound() {
//         int empId = 10;
//         CustomerRequestDTO requestDTO = mock(CustomerRequestDTO.class);
//         when(requestDTO.salesRepEmployeeNumber()).thenReturn(empId);
// 
//         Employee employee = new Employee();
//         when(employeeRepo.findById(empId)).thenReturn(Optional.of(employee));
// 
//         Customer entity = new Customer();
//         Customer savedEntity = new Customer();
//         CustomerResponseDTO responseDTO = mock(CustomerResponseDTO.class);
// 
//         when(mapper.toEntity(requestDTO)).thenReturn(entity);
//         when(repo.save(entity)).thenReturn(savedEntity);
//         when(mapper.toResponseDTO(savedEntity)).thenReturn(responseDTO);
// 
//         CustomerResponseDTO result = service.create(requestDTO);
// 
//         assertNotNull(result);
//         assertEquals(responseDTO, result);
//         verify(employeeRepo).findById(empId);
//         verify(repo).save(entity);
//     }
// 
//     @Test
//     void create_ThrowsException_WhenSalesRepIsNotNullAndNotFound() {
//         int empId = 10;
//         CustomerRequestDTO requestDTO = mock(CustomerRequestDTO.class);
//         when(requestDTO.salesRepEmployeeNumber()).thenReturn(empId);
// 
//         when(employeeRepo.findById(empId)).thenReturn(Optional.empty());
// 
//         RuntimeException exception = assertThrows(RuntimeException.class, () -> service.create(requestDTO));
//         assertEquals("Employee 10 (sales rep) not found", exception.getMessage());
// 
//         verify(employeeRepo).findById(empId);
//         verify(repo, never()).save(any());
//     }
// 
//     @Test
//     void update_ReturnsCustomer_WhenSalesRepIsNull() {
//         int id = 1;
//         CustomerRequestDTO requestDTO = mock(CustomerRequestDTO.class);
//         when(requestDTO.salesRepEmployeeNumber()).thenReturn(null);
// 
//         Customer existing = new Customer();
//         when(repo.findById(id)).thenReturn(Optional.of(existing));
// 
//         CustomerResponseDTO responseDTO = mock(CustomerResponseDTO.class);
//         doNothing().when(mapper).copyToEntity(requestDTO, existing);
//         doNothing().when(repo).update(existing);
//         when(mapper.toResponseDTO(existing)).thenReturn(responseDTO);
// 
//         CustomerResponseDTO result = service.update(id, requestDTO);
// 
//         assertNotNull(result);
//         assertEquals(responseDTO, result);
//         verify(employeeRepo, never()).findById(anyInt());
//         verify(mapper).copyToEntity(requestDTO, existing);
//         verify(repo).update(existing);
//         assertEquals(id, existing.getCustomerNumber());
//     }
// 
//     @Test
//     void update_ReturnsCustomer_WhenSalesRepIsNotNullAndFound() {
//         int id = 1;
//         int empId = 10;
//         CustomerRequestDTO requestDTO = mock(CustomerRequestDTO.class);
//         when(requestDTO.salesRepEmployeeNumber()).thenReturn(empId);
// 
//         Customer existing = new Customer();
//         when(repo.findById(id)).thenReturn(Optional.of(existing));
// 
//         Employee employee = new Employee();
//         when(employeeRepo.findById(empId)).thenReturn(Optional.of(employee));
// 
//         CustomerResponseDTO responseDTO = mock(CustomerResponseDTO.class);
//         doNothing().when(mapper).copyToEntity(requestDTO, existing);
//         doNothing().when(repo).update(existing);
//         when(mapper.toResponseDTO(existing)).thenReturn(responseDTO);
// 
//         CustomerResponseDTO result = service.update(id, requestDTO);
// 
//         assertNotNull(result);
//         assertEquals(responseDTO, result);
//         verify(employeeRepo).findById(empId);
//         verify(mapper).copyToEntity(requestDTO, existing);
//         verify(repo).update(existing);
//         assertEquals(id, existing.getCustomerNumber());
//     }
// 
//     @Test
//     void update_ThrowsException_WhenCustomerNotFound() {
//         int id = 1;
//         CustomerRequestDTO requestDTO = mock(CustomerRequestDTO.class);
//         when(repo.findById(id)).thenReturn(Optional.empty());
// 
//         RuntimeException exception = assertThrows(RuntimeException.class, () -> service.update(id, requestDTO));
//         assertEquals("Customer 1 not found", exception.getMessage());
//     }
// 
//     @Test
//     void update_ThrowsException_WhenSalesRepIsNotNullAndNotFound() {
//         int id = 1;
//         int empId = 10;
//         CustomerRequestDTO requestDTO = mock(CustomerRequestDTO.class);
//         when(requestDTO.salesRepEmployeeNumber()).thenReturn(empId);
// 
//         Customer existing = new Customer();
//         when(repo.findById(id)).thenReturn(Optional.of(existing));
//         when(employeeRepo.findById(empId)).thenReturn(Optional.empty());
// 
//         RuntimeException exception = assertThrows(RuntimeException.class, () -> service.update(id, requestDTO));
//         assertEquals("Employee 10 (sales rep) not found", exception.getMessage());
//     }
// 
//     @Test
//     void delete_DeletesCustomer_WhenFound() {
//         int id = 1;
//         Customer existing = new Customer();
//         when(repo.findById(id)).thenReturn(Optional.of(existing));
// 
//         service.delete(id);
// 
//         verify(repo).findById(id);
//         verify(repo).delete(id);
//     }
// 
//     @Test
//     void delete_ThrowsException_WhenNotFound() {
//         int id = 1;
//         when(repo.findById(id)).thenReturn(Optional.empty());
// 
//         RuntimeException exception = assertThrows(RuntimeException.class, () -> service.delete(id));
//         assertEquals("Customer 1 not found", exception.getMessage());
//         verify(repo, never()).delete(anyInt());
//     }
// 
//     @Test
//     void findAllPaged_ReturnsPageResponse_WithCalculatedTotalPages() {
//         int page = 1;
//         int size = 2;
//         String sortBy = "customerName";
//         boolean asc = true;
//         long total = 5L;
// 
//         List<Customer> customers = List.of(new Customer(), new Customer());
//         when(repo.findAllPaged(page, size, sortBy, asc)).thenReturn(customers);
//         when(mapper.toResponseDTO(any(Customer.class))).thenReturn(mock(CustomerResponseDTO.class));
//         when(repo.countAll()).thenReturn(total);
// 
//         PageResponse<CustomerResponseDTO> result = service.findAllPaged(page, size, sortBy, asc);
// 
//         assertNotNull(result);
//         assertEquals(page, result.page());
//         assertEquals(size, result.size());
//         assertEquals(3, result.totalPages()); // 5 items / size 2 = 2.5 -> ceil -> 3 pages
// 
//         verify(repo).findAllPaged(page, size, sortBy, asc);
//         verify(repo).countAll();
//         verify(mapper, times(2)).toResponseDTO(any(Customer.class));
//     }
// 
//     @Test
//     void findAllPaged_ReturnsEmptyPageResponse_WhenNoCustomers() {
//         int page = 1;
//         int size = 2;
//         String sortBy = "customerName";
//         boolean asc = true;
// 
//         when(repo.findAllPaged(page, size, sortBy, asc)).thenReturn(List.of());
//         when(repo.countAll()).thenReturn(0L);
// 
//         PageResponse<CustomerResponseDTO> result = service.findAllPaged(page, size, sortBy, asc);
// 
//         assertNotNull(result);
//         assertEquals(size, result.size());
//         assertEquals(0, result.totalPages());
//         assertEquals(0L, result.totalElements());
// 
//         verify(repo).findAllPaged(page, size, sortBy, asc);
//         verify(repo).countAll();
//         verify(mapper, never()).toResponseDTO(any());
//     }
// 
//     @Test
//     void createBulk_SavesAllCustomers() {
//         // Arrange
//         List<CustomerRequestDTO> requestDTOs = List.of(
//             mock(CustomerRequestDTO.class),
//             mock(CustomerRequestDTO.class)
//         );
//         Customer entity1 = new Customer();
//         Customer entity2 = new Customer();
//         
//         when(mapper.toEntity(requestDTOs.get(0))).thenReturn(entity1);
//         when(mapper.toEntity(requestDTOs.get(1))).thenReturn(entity2);
// 
//         // Act
//         service.createBulk(requestDTOs);
// 
//         // Assert
//         verify(mapper, times(2)).toEntity(any(CustomerRequestDTO.class));
//         verify(repo).saveAll(List.of(entity1, entity2));
//     }
// 
//     @Test
//     void createBulk_DoesNothing_WhenListIsEmpty() {
//         // Arrange
//         List<CustomerRequestDTO> requestDTOs = List.of();
// 
//         // Act
//         service.createBulk(requestDTOs);
// 
//         // Assert
//         verify(mapper, never()).toEntity(any());
//         verify(repo).saveAll(List.of());
//     }
// }