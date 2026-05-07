// package com.hunor.classicmodelsbackend.service;
// 
// import java.util.List;
// import java.util.Optional;
// 
// import org.junit.jupiter.api.AfterEach;
// import static org.junit.jupiter.api.Assertions.assertThrows;
// import org.junit.jupiter.api.Test;
// import static org.mockito.ArgumentMatchers.any;
// import static org.mockito.ArgumentMatchers.anyBoolean;
// import static org.mockito.ArgumentMatchers.anyInt;
// import static org.mockito.Mockito.mock;
// import static org.mockito.Mockito.never;
// import static org.mockito.Mockito.times;
// import static org.mockito.Mockito.verify;
// import static org.mockito.Mockito.when;
// import org.springframework.beans.factory.annotation.Autowired;
// import org.springframework.boot.test.context.SpringBootTest;
// import org.springframework.boot.test.context.TestConfiguration;
// import org.springframework.cache.CacheManager;
// import org.springframework.cache.annotation.EnableCaching;
// import org.springframework.context.annotation.Import;
// import org.springframework.test.context.bean.override.mockito.MockitoBean;
// 
// import com.hunor.classicmodelsbackend.AbstractIntegrationTest;
// import com.hunor.classicmodelsbackend.dto.customer.CustomerRequestDTO;
// import com.hunor.classicmodelsbackend.dto.customer.CustomerResponseDTO;
// import com.hunor.classicmodelsbackend.mapper.CustomerMapper;
// import com.hunor.classicmodelsbackend.model.Customer;
// import com.hunor.classicmodelsbackend.repository.CustomerRepository;
// import com.hunor.classicmodelsbackend.repository.EmployeeRepository;
// 
// /**
//  * Goal: Verify how the CustomerService interacts with the Spring framework (e.g., Caching).
//  * We extend AbstractIntegrationTest so the context has a valid database to boot up with.
//  * - an integration test that loads the spring framework but mocks the database layer
//  * - it uses the @SpringBootTest to boot up the spring application context, but it uses @MockitoBean to replace the real database repositories with Mockito fakes
//  * - it uses @TestConfiguration to explicitly enable caching
//  * - it verifies how your service interacts with spring managed features, specifically the @Cacheable annotations
//  * - it ensures your framework configurations (caching, transactions etc) are wired up correctly without the overhead and complexity of managing real database taste
//  */
// @SpringBootTest
// @Import(CustomerServiceIT.CacheTestConfig.class)
// class CustomerServiceIT extends AbstractIntegrationTest {
// 
//     // Enables Spring Caching specifically for this test context
//     @TestConfiguration
//     @EnableCaching
//     static class CacheTestConfig {
//     }
// 
//     @Autowired
//     private CustomerService customerService;
// 
//     @Autowired
//     private CacheManager cacheManager;
// 
//     // We use @MockitoBean to replace real repositories inside the Spring Context
//     // so we can test the caching wrapper of the CustomerService without real DB hits.
//     @MockitoBean
//     private CustomerRepository customerRepository;
// 
//     @MockitoBean
//     private CustomerMapper customerMapper;
// 
//     @MockitoBean
//     private EmployeeRepository employeeRepository;
// 
//     @AfterEach
//     void tearDown() {
//         // Clear caches after each test to ensure tests do not interfere with one another
//         cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
//     }
// 
//     @Test
//     void findById_ShouldCacheResult_WhenCalledMultipleTimes() {
//         int customerId = 100;
//         when(customerRepository.findById(customerId)).thenReturn(Optional.of(new Customer()));
//         when(customerMapper.toResponseDTO(any())).thenReturn(mock(CustomerResponseDTO.class));
// 
//         // Act: Call the method twice
//         customerService.findById(customerId);
//         customerService.findById(customerId);
// 
//         // Assert: The repository and mapper should only be interacted with ONCE
//         // The second call is served directly from the Spring cache
//         verify(customerRepository, times(1)).findById(customerId);
//         verify(customerMapper, times(1)).toResponseDTO(any());
//     }
// 
//     @Test
//     void findById_ShouldThrowExceptionAndNotCache_WhenCustomerNotFound() {
//         int customerId = 999;
//         when(customerRepository.findById(customerId)).thenReturn(Optional.empty());
// 
//         // Act & Assert: First call throws exception
//         assertThrows(RuntimeException.class, () -> customerService.findById(customerId));
// 
//         // Act & Assert: Second call also throws exception and hits the repository again
//         assertThrows(RuntimeException.class, () -> customerService.findById(customerId));
// 
//         // Since it threw an exception, it shouldn't be cached, meaning the repo is called twice
//         verify(customerRepository, times(2)).findById(customerId);
//     }
// 
//     @Test
//     void findAll_ShouldCacheResult_WhenCalledMultipleTimes() {
//         when(customerRepository.findAll()).thenReturn(List.of(new Customer()));
//         when(customerMapper.toResponseDTO(any())).thenReturn(mock(CustomerResponseDTO.class));
// 
//         // Act: Call the method twice
//         customerService.findAll();
//         customerService.findAll();
// 
//         // Assert: The repository should only be interacted with ONCE
//         // The second call is served directly from the Spring cache
//         verify(customerRepository, times(1)).findAll();
//         verify(customerMapper, times(1)).toResponseDTO(any());
//     }
// 
//     @Test
//     void create_ShouldPutInCache_SoSubsequentFindByIdHitsCache() {
//         int newCustomerId = 200;
//         CustomerRequestDTO requestDTO = mock(CustomerRequestDTO.class);
// 
//         // Force the mock to return null to bypass the employee validation check
//         when(requestDTO.salesRepEmployeeNumber()).thenReturn(null);
// 
//         Customer entity = new Customer();
//         Customer savedEntity = new Customer();
//         CustomerResponseDTO responseDTO = mock(CustomerResponseDTO.class);
// 
//         // We MUST mock customerNumber() so the @CachePut knows what key to use in the cache
//         when(responseDTO.customerNumber()).thenReturn(newCustomerId);
//         when(customerMapper.toEntity(requestDTO)).thenReturn(entity);
//         when(customerRepository.save(entity)).thenReturn(savedEntity);
//         when(customerMapper.toResponseDTO(savedEntity)).thenReturn(responseDTO);
// 
//         // Act: Create the customer (places the result in the 'customers' cache)
//         customerService.create(requestDTO);
// 
//         // Act: Find by ID (should hit the cache, NOT the database)
//         customerService.findById(newCustomerId);
// 
//         // Assert: The repository's findById should NEVER be called
//         verify(customerRepository, never()).findById(newCustomerId);
//     }
// 
//     @Test
//     void create_ShouldEvictCustomersAllCache_WhenCalled() {
//         CustomerRequestDTO requestDTO = mock(CustomerRequestDTO.class);
// 
//         // Force the mock to return null to bypass the employee validation check
//         when(requestDTO.salesRepEmployeeNumber()).thenReturn(null);
// 
//         CustomerResponseDTO responseDTO = mock(CustomerResponseDTO.class);
//         when(responseDTO.customerNumber()).thenReturn(300);
// 
//         when(customerRepository.findAll()).thenReturn(List.of(new Customer()));
//         when(customerRepository.save(any())).thenReturn(new Customer());
//         when(customerMapper.toResponseDTO(any(Customer.class))).thenReturn(responseDTO);
// 
//         customerService.findAll();      // Cache Miss -> hits repo (1st time)
//         customerService.create(requestDTO); // Evicts the "customersAll" cache
//         customerService.findAll();      // Cache Miss (because it was evicted) -> hits repo (2nd time)
// 
//         verify(customerRepository, times(2)).findAll();
//     }
// 
//     @Test
//     void update_ShouldPutInCache_SoSubsequentFindByIdHitsCache() {
//         int customerId = 400;
//         CustomerRequestDTO requestDTO = mock(CustomerRequestDTO.class);
//         when(requestDTO.salesRepEmployeeNumber()).thenReturn(null);
// 
//         Customer existing = new Customer();
//         CustomerResponseDTO responseDTO = mock(CustomerResponseDTO.class);
// 
//         when(customerRepository.findById(customerId)).thenReturn(Optional.of(existing));
//         when(customerMapper.toResponseDTO(existing)).thenReturn(responseDTO);
// 
//         // Act: Update the customer (places the result in the 'customers' cache)
//         customerService.update(customerId, requestDTO);
// 
//         // Act: Find by ID (should hit the cache, NOT the database)
//         customerService.findById(customerId);
// 
//         // Assert: The repository's findById should only be called ONCE (during the update itself).
//         // The subsequent findById call gets served directly from the Spring cache.
//         verify(customerRepository, times(1)).findById(customerId);
//     }
// 
//     @Test
//     void update_ShouldEvictCustomersAllCache_WhenCalled() {
//         int customerId = 500;
//         CustomerRequestDTO requestDTO = mock(CustomerRequestDTO.class);
//         when(requestDTO.salesRepEmployeeNumber()).thenReturn(null);
//         CustomerResponseDTO responseDTO = mock(CustomerResponseDTO.class);
// 
//         when(customerRepository.findById(customerId)).thenReturn(Optional.of(new Customer()));
//         when(customerRepository.findAll()).thenReturn(List.of(new Customer()));
//         when(customerMapper.toResponseDTO(any(Customer.class))).thenReturn(responseDTO);
// 
//         customerService.findAll();      // Cache Miss -> hits repo (1st time)
//         customerService.update(customerId, requestDTO); // Evicts the "customersAll" cache
//         customerService.findAll();      // Cache Miss (because it was evicted) -> hits repo (2nd time)
// 
//         verify(customerRepository, times(2)).findAll();
//     }
// 
//     @Test
//     void delete_ShouldEvictCustomersCache_WhenCalled() {
//         int customerId = 600;
//         Customer existing = new Customer();
//         when(customerMapper.toResponseDTO(any())).thenReturn(mock(CustomerResponseDTO.class));
// 
//         // Define sequential behavior for findById
//         when(customerRepository.findById(customerId))
//                 .thenReturn(Optional.of(existing)) // 1. For the initial find to populate the cache
//                 .thenReturn(Optional.of(existing)) // 2. For the internal check in delete()
//                 .thenReturn(Optional.empty());     // 3. For the find after deletion, which should fail
// 
//         // Act: Find by ID (Cache miss -> hits repo 1st time)
//         customerService.findById(customerId);
// 
//         // Act: Delete the customer (Evicts "customers" cache. Internally hits repo.findById 2nd time)
//         customerService.delete(customerId);
// 
//         // Act: Find by ID again (Cache miss because it was evicted -> hits repo 3rd time, gets Optional.empty)
//         assertThrows(RuntimeException.class, () -> customerService.findById(customerId));
// 
//         // Assert: Verify the sequence of interactions
//         verify(customerRepository, times(3)).findById(customerId);
//         verify(customerRepository, times(1)).delete(customerId);
//     }
// 
//     @Test
//     void delete_ShouldEvictCustomersAllCache_WhenCalled() {
//         int customerId = 700;
//         when(customerRepository.findById(customerId)).thenReturn(Optional.of(new Customer()));
//         when(customerRepository.findAll()).thenReturn(List.of(new Customer()));
//         when(customerMapper.toResponseDTO(any(Customer.class))).thenReturn(mock(CustomerResponseDTO.class));
// 
//         customerService.findAll();          // Cache miss -> hits repo (1st time)
//         customerService.delete(customerId); // Evicts the "customersAll" cache
//         customerService.findAll();          // Cache miss (because it was evicted) -> hits repo (2nd time)
// 
//         verify(customerRepository, times(2)).findAll();
//     }
// 
//     @Test
//     void findAllPaged_ShouldCacheResult_WhenCalledMultipleTimesWithSameParams() {
//         int page = 1;
//         int size = 10;
//         String sortBy = "customerName";
//         boolean asc = true;
// 
//         when(customerRepository.findAllPaged(page, size, sortBy, asc)).thenReturn(List.of(new Customer()));
//         when(customerRepository.countAll()).thenReturn(1L);
//         when(customerMapper.toResponseDTO(any(Customer.class))).thenReturn(mock(CustomerResponseDTO.class));
// 
//         // Act: Call the method twice with identical parameters
//         customerService.findAllPaged(page, size, sortBy, asc);
//         customerService.findAllPaged(page, size, sortBy, asc);
// 
//         // Assert: The repository should only be interacted with ONCE
//         // The second call is served directly from the Spring cache
//         verify(customerRepository, times(1)).findAllPaged(page, size, sortBy, asc);
//         verify(customerRepository, times(1)).countAll();
//         verify(customerMapper, times(1)).toResponseDTO(any(Customer.class));
//     }
// 
//     @Test
//     void findAllPaged_ShouldNotCacheResult_WhenCalledWithDifferentParams() {
//         int page1 = 1;
//         int page2 = 2;
//         int size = 10;
//         String sortBy = "customerName";
//         boolean asc = true;
// 
//         when(customerRepository.findAllPaged(anyInt(), anyInt(), any(), anyBoolean())).thenReturn(List.of(new Customer()));
//         when(customerRepository.countAll()).thenReturn(1L);
//         when(customerMapper.toResponseDTO(any(Customer.class))).thenReturn(mock(CustomerResponseDTO.class));
// 
//         // Act: Call the method twice with different parameters
//         customerService.findAllPaged(page1, size, sortBy, asc);
//         customerService.findAllPaged(page2, size, sortBy, asc);
// 
//         // Assert: The repository should be interacted with TWICE since it's a cache miss
//         verify(customerRepository, times(1)).findAllPaged(page1, size, sortBy, asc);
//         verify(customerRepository, times(1)).findAllPaged(page2, size, sortBy, asc);
//         verify(customerRepository, times(2)).countAll();
//         verify(customerMapper, times(2)).toResponseDTO(any(Customer.class));
//     }
// 
//     @Test
//     void createBulk_ShouldEvictCustomersAllCache_WhenCalled() {
//         List<CustomerRequestDTO> requestDTOs = List.of(mock(CustomerRequestDTO.class), mock(CustomerRequestDTO.class));
// 
//         when(customerRepository.findAll()).thenReturn(List.of(new Customer()));
//         when(customerMapper.toEntity(any(CustomerRequestDTO.class))).thenReturn(new Customer());
// 
//         customerService.findAll();              // Cache Miss -> hits repo (1st time)
//         customerService.createBulk(requestDTOs); // Evicts the "customersAll" cache
//         customerService.findAll();              // Cache Miss (because it was evicted) -> hits repo (2nd time)
// 
//         verify(customerRepository, times(2)).findAll();
//         verify(customerRepository, times(1)).saveAll(any());
//     }
// }