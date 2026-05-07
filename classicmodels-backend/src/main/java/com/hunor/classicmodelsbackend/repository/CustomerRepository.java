package com.hunor.classicmodelsbackend.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

import com.hunor.classicmodelsbackend.model.Customer;
import org.springframework.stereotype.Repository;

@Repository
public class CustomerRepository {

    private final DataSource dataSource;
    public CustomerRepository(DataSource dataSource) { this.dataSource = dataSource; }

    public Customer save(Customer c) {
        String sql = """
            INSERT INTO customers (
                customerName, contactLastName, contactFirstName,
                phone, addressLine1, addressLine2, city, state, postalCode,
                country, salesRepEmployeeNumber, creditLimit
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {

            pstmt.setString(1, c.getCustomerName());
            pstmt.setString(2, c.getContactLastName());
            pstmt.setString(3, c.getContactFirstName());
            pstmt.setString(4, c.getPhone());
            pstmt.setString(5, c.getAddressLine1());
            pstmt.setString(6, c.getAddressLine2());
            pstmt.setString(7, c.getCity());
            pstmt.setString(8, c.getState());
            pstmt.setString(9, c.getPostalCode());
            pstmt.setString(10, c.getCountry());
            pstmt.setObject(11, c.getSalesRepEmployeeNumber());
            pstmt.setBigDecimal(12, c.getCreditLimit());

            pstmt.executeUpdate();

            try (ResultSet keys = pstmt.getGeneratedKeys()) {
                if (keys.next()) {
                    c.setCustomerNumber(keys.getInt(1));
                }
            }
            return c;
        }

        catch (SQLException e) {
            throw new RuntimeException("Insert failed", e);
        }
    }

    public Optional<Customer> findById(int id) {
        String sql = "SELECT * FROM customers WHERE customerNumber = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Find by ID failed", e);
        }
    }

    public List<Customer> findAll() {
        String sql = "SELECT * FROM customers";
        List<Customer> customers = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                customers.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Find all failed", e);
        }

        return customers;
    }

    public void update(Customer c) {
        String sql = """
            UPDATE customers SET
                customerName = ?, contactLastName = ?, contactFirstName = ?,
                phone = ?, addressLine1 = ?, addressLine2 = ?, city = ?, state = ?,
                postalCode = ?, country = ?, salesRepEmployeeNumber = ?, creditLimit = ?
            WHERE customerNumber = ?
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, c.getCustomerName());
            pstmt.setString(2, c.getContactLastName());
            pstmt.setString(3, c.getContactFirstName());
            pstmt.setString(4, c.getPhone());
            pstmt.setString(5, c.getAddressLine1());
            pstmt.setString(6, c.getAddressLine2());
            pstmt.setString(7, c.getCity());
            pstmt.setString(8, c.getState());
            pstmt.setString(9, c.getPostalCode());
            pstmt.setString(10, c.getCountry());
            pstmt.setObject(11, c.getSalesRepEmployeeNumber());
            pstmt.setBigDecimal(12, c.getCreditLimit());
            pstmt.setInt(13, c.getCustomerNumber());

            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Update failed", e);
        }
    }

    public void delete(int id) {
        String sql = "DELETE FROM customers WHERE customerNumber = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Delete failed", e);
        }
    }

    private Customer mapRow(ResultSet rs) throws SQLException {
        Customer c = new Customer();
        c.setCustomerNumber(rs.getInt("customerNumber"));
        c.setCustomerName(rs.getString("customerName"));
        c.setContactLastName(rs.getString("contactLastName"));
        c.setContactFirstName(rs.getString("contactFirstName"));
        c.setPhone(rs.getString("phone"));
        c.setAddressLine1(rs.getString("addressLine1"));
        c.setAddressLine2(rs.getString("addressLine2"));
        c.setCity(rs.getString("city"));
        c.setState(rs.getString("state"));
        c.setPostalCode(rs.getString("postalCode"));
        c.setCountry(rs.getString("country"));
        c.setSalesRepEmployeeNumber((Integer) rs.getObject("salesRepEmployeeNumber"));
        c.setCreditLimit(rs.getBigDecimal("creditLimit"));
        return c;
    }

    public List<Customer> findAllPaged(int page, int size, String sortBy, boolean asc) {
        if (page < 0 || size <= 0) throw new IllegalArgumentException("Invalid page/size");

        String order = asc ? "ASC" : "DESC";
        String sortColumn = switch (sortBy) {
            case "customerName", "city", "country", "creditLimit" -> sortBy;
            default -> "customerName";
        };
        String sql = ("SELECT * FROM customers ORDER BY %s %s LIMIT ? OFFSET ?").formatted(sortColumn, order);

        List<Customer> list = new ArrayList<>();
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, size);
            ps.setInt(2, page * size);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        } catch (SQLException e) { throw new RuntimeException("Find paged failed", e); }
        return list;
    }

    public long countAll() {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM customers");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getLong(1);
        } catch (SQLException e) { throw new RuntimeException("Count failed", e); }
    }

    public void saveAll(List<Customer> customers) {
        String sql = """
        INSERT INTO customers (
          customerName, contactLastName, contactFirstName,
          phone, addressLine1, addressLine2, city, state, postalCode,
          country, salesRepEmployeeNumber, creditLimit
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;

        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            int batch = 0;
            for (Customer x : customers) {
                ps.setString(1, x.getCustomerName());
                ps.setString(2, x.getContactLastName());
                ps.setString(3, x.getContactFirstName());
                ps.setString(4, x.getPhone());
                ps.setString(5, x.getAddressLine1());
                ps.setString(6, x.getAddressLine2());
                ps.setString(7, x.getCity());
                ps.setString(8, x.getState());
                ps.setString(9, x.getPostalCode());
                ps.setString(10, x.getCountry());
                ps.setObject(11, x.getSalesRepEmployeeNumber());
                ps.setBigDecimal(12, x.getCreditLimit());
                ps.addBatch();
                if (++batch % 500 == 0) ps.executeBatch();
            }
            ps.executeBatch();
        } catch (SQLException e) {
            throw new RuntimeException("Batch insert failed", e);
        }
    }
}
