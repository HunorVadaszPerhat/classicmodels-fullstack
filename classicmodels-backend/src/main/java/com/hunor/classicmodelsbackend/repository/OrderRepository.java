package com.hunor.classicmodelsbackend.repository;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

import org.springframework.stereotype.Repository;

import com.hunor.classicmodelsbackend.model.Order;

@Repository
public class OrderRepository {

    private final DataSource dataSource;

    public OrderRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public Order save(Order o) {
        final String insertAuto = """
            INSERT INTO orders (
                orderDate, requiredDate, shippedDate, status, comments, customerNumber
            ) VALUES (?, ?, ?, ?, ?, ?)
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     insertAuto,
                     PreparedStatement.RETURN_GENERATED_KEYS)) {

            ps.setDate(1, Date.valueOf(o.getOrderDate()));
            ps.setDate(2, Date.valueOf(o.getRequiredDate()));

            if (o.getShippedDate() != null) {
                ps.setDate(3, Date.valueOf(o.getShippedDate()));
            } else {
                ps.setNull(3, Types.DATE);
            }
            ps.setString(4, o.getStatus());
            ps.setString(5, o.getComments());
            ps.setInt(6, o.getCustomerNumber());

            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) o.setOrderNumber(keys.getInt(1));
            }
            return o;
        } catch (SQLException e) {
            throw new RuntimeException("Insert failed", e);
        }
    }

    public Optional<Order> findById(int id) {
        String sql = "SELECT * FROM orders WHERE orderNumber = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Find by ID failed", e);
        }
    }

    public List<Order> findAll() {
        String sql = "SELECT * FROM orders";
        List<Order> list = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) list.add(mapRow(rs));
            return list;
        } catch (SQLException e) {
            throw new RuntimeException("Find all failed", e);
        }
    }

    public void update(Order o) {
        String sql = """
            UPDATE orders SET
                orderDate = ?, requiredDate = ?, shippedDate = ?, status = ?, comments = ?, customerNumber = ?
            WHERE orderNumber = ?
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setDate(1, Date.valueOf(o.getOrderDate()));
            ps.setDate(2, Date.valueOf(o.getRequiredDate()));
            if (o.getShippedDate() != null) {
                ps.setDate(3, Date.valueOf(o.getShippedDate()));
            } else {
                ps.setNull(3, Types.DATE);
            }
            ps.setString(4, o.getStatus());
            ps.setString(5, o.getComments());
            ps.setInt(6, o.getCustomerNumber());
            ps.setInt(7, o.getOrderNumber());

            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Update failed", e);
        }
    }

    public void delete(int id) {
        String sql = "DELETE FROM orders WHERE orderNumber = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Delete failed", e);
        }
    }

    private Order mapRow(ResultSet rs) throws SQLException {
        var o = new Order();
        o.setOrderNumber(rs.getInt("orderNumber"));
        o.setOrderDate(rs.getDate("orderDate").toLocalDate());
        o.setRequiredDate(rs.getDate("requiredDate").toLocalDate());
        Date shipped = rs.getDate("shippedDate");
        o.setShippedDate(shipped != null ? shipped.toLocalDate() : null);
        o.setStatus(rs.getString("status"));
        o.setComments(rs.getString("comments"));
        o.setCustomerNumber(rs.getInt("customerNumber"));
        return o;
    }

    public List<Order> findAllPaged(int page, int size, String sortBy, boolean asc) {
        if (page < 0 || size <= 0) throw new IllegalArgumentException("Invalid page/size");
        String order = asc ? "ASC" : "DESC";
        String sortColumn = switch (sortBy) {
            case "orderDate", "requiredDate", "shippedDate", "status", "customerNumber" -> sortBy;
            default -> "orderDate";
        };
        String sql = ("SELECT * FROM orders ORDER BY %s %s LIMIT ? OFFSET ?").formatted(sortColumn, order);

        List<Order> list = new ArrayList<>();
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, size);
            ps.setInt(2, page * size);
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) list.add(mapRow(rs)); }
        } catch (SQLException e) { throw new RuntimeException("Find paged failed", e); }
        return list;
    }

    public long countAll() {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM orders");
             ResultSet rs = ps.executeQuery()) {
            rs.next(); return rs.getLong(1);
        } catch (SQLException e) { throw new RuntimeException("Count failed", e); }
    }

    public void saveAll(List<Order> orders) {
        final String insertAuto = """
            INSERT INTO orders (
                orderDate, requiredDate, shippedDate, status, comments, customerNumber
            ) VALUES (?, ?, ?, ?, ?, ?)
            """;

        try (Connection conn = dataSource.getConnection()) {
            for (Order o : orders) {
                try (PreparedStatement ps = conn.prepareStatement(
                        insertAuto,
                        PreparedStatement.RETURN_GENERATED_KEYS)) {

                    ps.setDate(1, java.sql.Date.valueOf(o.getOrderDate()));
                    ps.setDate(2, java.sql.Date.valueOf(o.getRequiredDate()));

                    ps.setObject(3, o.getShippedDate() == null ? null : java.sql.Date.valueOf(o.getShippedDate()));
                    ps.setString(4, o.getStatus());
                    ps.setString(5, o.getComments());
                    ps.setInt(6, o.getCustomerNumber());

                    ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        if (keys.next()) o.setOrderNumber(keys.getInt(1));
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Batch insert (orders) failed", e);
        }
    }
}
