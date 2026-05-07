// package com.hunor.classicmodelsbackend;
// 
// import org.springframework.test.context.DynamicPropertyRegistry;
// import org.springframework.test.context.DynamicPropertySource;
// import org.testcontainers.containers.MySQLContainer;
// import org.testcontainers.junit.jupiter.Container;
// import org.testcontainers.junit.jupiter.Testcontainers;
// import org.testcontainers.utility.MountableFile;
// 
// /**
//  * Base class for all Integration Tests.
//  * Goal: Ensure that all integration tests share the SAME Docker container.
//  * This makes the test suite run much faster because the MySQL container only boots up once.
//  */
// @Testcontainers
// public abstract class AbstractIntegrationTest {
// 
//     // Spins up a real MySQL Docker container before tests run.
//     @Container
//     static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
//             .withDatabaseName("classicmodels")
//             .withUsername("test")
//             .withPassword("test")
//             .withCopyFileToContainer(
//                     MountableFile.forHostPath("db/init/01-mysqlsampledatabase_auto_increment.sql"),
//                     "/docker-entrypoint-initdb.d/init.sql"
//             );
// 
//     // Dynamically overrides your application.properties so Spring connects to the Docker container instead of your local DB.
//     @DynamicPropertySource
//     static void configureProperties(DynamicPropertyRegistry registry) {
//         registry.add("spring.datasource.url", mysql::getJdbcUrl);
//         registry.add("spring.datasource.username", mysql::getUsername);
//         registry.add("spring.datasource.password", mysql::getPassword);
//     }
// }