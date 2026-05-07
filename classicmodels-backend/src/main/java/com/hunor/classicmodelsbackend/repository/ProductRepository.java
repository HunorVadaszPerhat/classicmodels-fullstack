package com.hunor.classicmodelsbackend.repository;

import com.hunor.classicmodelsbackend.model.Product;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ProductRepository {

    private final DataSource dataSource;

    public ProductRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public Product save(Product p) {
        String sql = """
            INSERT INTO products (
                productCode, productName, productLine, productScale,
                productVendor, productDescription, quantityInStock,
                buyPrice, MSRP
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            String generatedCode = generateProductCode();
            p.setProductCode(generatedCode);

            pstmt.setString(1, generatedCode);
            pstmt.setString(2, p.getProductName());
            pstmt.setString(3, p.getProductLine());
            pstmt.setString(4, p.getProductScale());
            pstmt.setString(5, p.getProductVendor());
            pstmt.setString(6, p.getProductDescription());
            pstmt.setInt(7, p.getQuantityInStock());
            pstmt.setBigDecimal(8, p.getBuyPrice());
            pstmt.setBigDecimal(9, p.getMsrp());

            pstmt.executeUpdate();
            return p;
        } catch (SQLException e) {
            throw new RuntimeException("Insert failed", e);
        }
    }

    public Optional<Product> findById(String code) {
        String sql = "SELECT * FROM products WHERE productCode = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, code);
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

    public List<Product> findAll() {
        String sql = "SELECT * FROM products";
        List<Product> products = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                products.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Find all failed", e);
        }

        return products;
    }

    public void update(Product p) {
        String sql = """
            UPDATE products SET
                productName = ?, productLine = ?, productScale = ?,
                productVendor = ?, productDescription = ?, quantityInStock = ?,
                buyPrice = ?, MSRP = ?
            WHERE productCode = ?
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, p.getProductName());
            pstmt.setString(2, p.getProductLine());
            pstmt.setString(3, p.getProductScale());
            pstmt.setString(4, p.getProductVendor());
            pstmt.setString(5, p.getProductDescription());
            pstmt.setInt(6, p.getQuantityInStock());
            pstmt.setBigDecimal(7, p.getBuyPrice());
            pstmt.setBigDecimal(8, p.getMsrp());
            pstmt.setString(9, p.getProductCode());

            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Update failed", e);
        }
    }

    public void delete(String code) {
        String sql = "DELETE FROM products WHERE productCode = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, code);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Delete failed", e);
        }
    }

    private Product mapRow(ResultSet rs) throws SQLException {
        Product p = new Product();
        p.setProductCode(rs.getString("productCode"));
        p.setProductName(rs.getString("productName"));
        p.setProductLine(rs.getString("productLine"));
        p.setProductScale(rs.getString("productScale"));
        p.setProductVendor(rs.getString("productVendor"));
        p.setProductDescription(rs.getString("productDescription"));
        p.setQuantityInStock(rs.getShort("quantityInStock"));
        p.setBuyPrice(rs.getBigDecimal("buyPrice"));
        p.setMsrp(rs.getBigDecimal("MSRP"));
        return p;
    }

    private String generateProductCode() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 13);
    }

    public List<Product> findAllPaged(int page, int size, String sortBy, boolean asc) {
        if (page < 0 || size <= 0) throw new IllegalArgumentException("Invalid page/size");
        String order = asc ? "ASC" : "DESC";

        String sortColumn = switch (sortBy) {
            case "productName", "productLine", "buyPrice", "MSRP", "quantityInStock" -> sortBy;
            default -> "productName";
        };
        String sql = """
        SELECT * FROM products
        ORDER BY %s %s
        LIMIT ? OFFSET ?
        """.formatted(sortColumn, order);

        List<Product> products = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, size);
            ps.setInt(2, page * size);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) products.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Find paged failed", e);
        }
        return products;
    }

    public long countAll() {
        String sql = "SELECT COUNT(*) FROM products";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getLong(1);
        } catch (SQLException e) {
            throw new RuntimeException("Count failed", e);
        }
    }

    public void saveAll(List<Product> products) {
        String sql = """
        INSERT INTO products (
            productCode, productName, productLine, productScale,
            productVendor, productDescription, quantityInStock, buyPrice, MSRP
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            int batch = 0;
            for (Product p : products) {
                String code = generateProductCode();
                p.setProductCode(code);

                ps.setString(1, code);
                ps.setString(2, p.getProductName());
                ps.setString(3, p.getProductLine());
                ps.setString(4, p.getProductScale());
                ps.setString(5, p.getProductVendor());
                ps.setString(6, p.getProductDescription());
                ps.setInt(7, p.getQuantityInStock());
                ps.setBigDecimal(8, p.getBuyPrice());
                ps.setBigDecimal(9, p.getMsrp());
                ps.addBatch();

                if (++batch % 500 == 0) ps.executeBatch();
            }
            ps.executeBatch();
        } catch (SQLException e) {
            throw new RuntimeException("Batch insert failed", e);
        }
    }
}
