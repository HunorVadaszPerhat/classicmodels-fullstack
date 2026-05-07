package com.hunor.classicmodelsbackend.repository;

import com.hunor.classicmodelsbackend.model.Payment;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class PaymentRepository {

    private final DataSource dataSource;

    public PaymentRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public Payment save(Payment p) {
        String sql = """
            INSERT INTO payments (customerNumber, checkNumber, paymentDate, amount)
            VALUES (?, ?, ?, ?)
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, p.getCustomerNumber());
            ps.setString(2, p.getCheckNumber());
            ps.setDate(3, Date.valueOf(p.getPaymentDate()));
            ps.setBigDecimal(4, p.getAmount());

            ps.executeUpdate();
            return p;
        } catch (SQLException e) {
            throw new RuntimeException("Insert failed", e);
        }
    }

    public Optional<Payment> findById(int customerNumber, String checkNumber) {
        String sql = "SELECT * FROM payments WHERE customerNumber = ? AND checkNumber = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, customerNumber);
            ps.setString(2, checkNumber);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Find by ID failed", e);
        }
    }

    public List<Payment> findAll() {
        String sql = "SELECT * FROM payments";
        List<Payment> list = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) list.add(mapRow(rs));
            return list;
        } catch (SQLException e) {
            throw new RuntimeException("Find all failed", e);
        }
    }

    public void update(Payment p) {
        String sql = """
            UPDATE payments SET paymentDate = ?, amount = ?
            WHERE customerNumber = ? AND checkNumber = ?
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setDate(1, Date.valueOf(p.getPaymentDate()));
            ps.setBigDecimal(2, p.getAmount());
            ps.setInt(3, p.getCustomerNumber());
            ps.setString(4, p.getCheckNumber());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Update failed", e);
        }
    }

    public void delete(int customerNumber, String checkNumber) {
        String sql = "DELETE FROM payments WHERE customerNumber = ? AND checkNumber = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, customerNumber);
            ps.setString(2, checkNumber);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Delete failed", e);
        }
    }

    private Payment mapRow(ResultSet rs) throws SQLException {
        var p = new Payment();
        p.setCustomerNumber(rs.getInt("customerNumber"));
        p.setCheckNumber(rs.getString("checkNumber"));
        p.setPaymentDate(rs.getDate("paymentDate").toLocalDate());
        p.setAmount(rs.getBigDecimal("amount"));
        return p;
    }

    public List<Payment> findAllPaged(int page, int size, String sortBy, boolean asc) {
        if (page < 0 || size <= 0) throw new IllegalArgumentException("Invalid page/size");
        String order = asc ? "ASC" : "DESC";
        String sortColumn = switch (sortBy) {
            case "paymentDate", "amount", "customerNumber", "checkNumber" -> sortBy;
            default -> "paymentDate";
        };
        String sql = ("SELECT * FROM payments ORDER BY %s %s LIMIT ? OFFSET ?").formatted(sortColumn, order);

        List<Payment> list = new ArrayList<>();
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, size);
            ps.setInt(2, page * size);
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) list.add(mapRow(rs)); }
        } catch (SQLException e) { throw new RuntimeException("Find paged failed", e); }
        return list;
    }

    public long countAll() {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM payments");
             ResultSet rs = ps.executeQuery()) {
            rs.next(); return rs.getLong(1);
        } catch (SQLException e) { throw new RuntimeException("Count failed", e); }
    }

    public void saveAll(List<Payment> items) {
        final String sql = """
        INSERT INTO payments (
            customerNumber, checkNumber, paymentDate, amount
        ) VALUES (?, ?, ?, ?)
        """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            int batch = 0;
            for (Payment p : items) {
                ps.setInt(1, p.getCustomerNumber());
                ps.setString(2, p.getCheckNumber());
                ps.setDate(3, java.sql.Date.valueOf(p.getPaymentDate()));
                ps.setBigDecimal(4, p.getAmount());
                ps.addBatch();

                if (++batch % 1000 == 0) ps.executeBatch();
            }
            ps.executeBatch();
        } catch (SQLException e) {
            throw new RuntimeException("Batch insert (payments) failed", e);
        }
    }
}
