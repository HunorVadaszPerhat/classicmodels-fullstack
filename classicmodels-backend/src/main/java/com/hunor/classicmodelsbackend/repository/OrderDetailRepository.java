package com.hunor.classicmodelsbackend.repository;

import com.hunor.classicmodelsbackend.model.OrderDetail;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class OrderDetailRepository {

    private final DataSource dataSource;

    public OrderDetailRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public OrderDetail save(OrderDetail od) {
        String sql = """
            INSERT INTO orderdetails (
                orderNumber, productCode, quantityOrdered, priceEach, orderLineNumber
            ) VALUES (?, ?, ?, ?, ?)
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, od.getOrderNumber());
            ps.setString(2, od.getProductCode());
            ps.setInt(3, od.getQuantityOrdered());
            ps.setBigDecimal(4, od.getPriceEach());
            ps.setInt(5, od.getOrderLineNumber());

            ps.executeUpdate();
            return od;
        } catch (SQLException e) {
            throw new RuntimeException("Insert failed", e);
        }
    }

    public Optional<OrderDetail> findById(int orderNumber, String productCode) {
        String sql = "SELECT * FROM orderdetails WHERE orderNumber = ? AND productCode = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, orderNumber);
            ps.setString(2, productCode);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Find by ID failed", e);
        }
    }

    public List<OrderDetail> findAll() {
        String sql = "SELECT * FROM orderdetails";
        List<OrderDetail> list = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) list.add(mapRow(rs));
            return list;
        } catch (SQLException e) {
            throw new RuntimeException("Find all failed", e);
        }
    }

    public void update(OrderDetail od) {
        String sql = """
            UPDATE orderdetails SET
                quantityOrdered = ?, priceEach = ?, orderLineNumber = ?
            WHERE orderNumber = ? AND productCode = ?
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, od.getQuantityOrdered());
            ps.setBigDecimal(2, od.getPriceEach());
            ps.setInt(3, od.getOrderLineNumber());
            ps.setInt(4, od.getOrderNumber());
            ps.setString(5, od.getProductCode());

            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Update failed", e);
        }
    }

    public void delete(int orderNumber, String productCode) {
        String sql = "DELETE FROM orderdetails WHERE orderNumber = ? AND productCode = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, orderNumber);
            ps.setString(2, productCode);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Delete failed", e);
        }
    }

    private OrderDetail mapRow(ResultSet rs) throws SQLException {
        var od = new OrderDetail();
        od.setOrderNumber(rs.getInt("orderNumber"));
        od.setProductCode(rs.getString("productCode"));
        od.setQuantityOrdered(rs.getInt("quantityOrdered"));
        od.setPriceEach(rs.getBigDecimal("priceEach"));
        od.setOrderLineNumber(rs.getShort("orderLineNumber"));
        return od;
    }

    public List<OrderDetail> findAllPaged(int page, int size, String sortBy, boolean asc) {
        if (page < 0 || size <= 0) throw new IllegalArgumentException("Invalid page/size");
        String order = asc ? "ASC" : "DESC";
        String sortColumn = switch (sortBy) {
            case "orderNumber", "productCode", "quantityOrdered", "priceEach", "orderLineNumber" -> sortBy;
            default -> "orderNumber";
        };
        String sql = ("SELECT * FROM orderdetails ORDER BY %s %s LIMIT ? OFFSET ?").formatted(sortColumn, order);

        List<OrderDetail> list = new ArrayList<>();
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, size);
            ps.setInt(2, page * size);
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) list.add(mapRow(rs)); }
        } catch (SQLException e) { throw new RuntimeException("Find paged failed", e); }
        return list;
    }

    public long countAll() {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM orderdetails");
             ResultSet rs = ps.executeQuery()) {
            rs.next(); return rs.getLong(1);
        } catch (SQLException e) { throw new RuntimeException("Count failed", e); }
    }

    public void saveAll(List<OrderDetail> items) {
        final String sql = """
        INSERT INTO orderdetails (
            orderNumber, productCode, quantityOrdered, priceEach, orderLineNumber
        ) VALUES (?, ?, ?, ?, ?)
        """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            int batch = 0;
            for (OrderDetail d : items) {
                ps.setInt(1, d.getOrderNumber());
                ps.setString(2, d.getProductCode());
                ps.setInt(3, d.getQuantityOrdered());
                ps.setBigDecimal(4, d.getPriceEach());
                ps.setInt(5, d.getOrderLineNumber());
                ps.addBatch();

                if (++batch % 1000 == 0) ps.executeBatch();
            }
            ps.executeBatch();
        } catch (SQLException e) {
            throw new RuntimeException("Batch insert (orderdetails) failed", e);
        }
    }
}
