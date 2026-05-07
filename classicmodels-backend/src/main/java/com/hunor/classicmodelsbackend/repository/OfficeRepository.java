package com.hunor.classicmodelsbackend.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.stereotype.Repository;

import com.hunor.classicmodelsbackend.model.Office;

@Repository
public class OfficeRepository {

    private final DataSource dataSource;

    public OfficeRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public Office save(Office o) {
        String sql = """
            INSERT INTO offices (
                officeCode, city, phone, addressLine1, addressLine2,
                state, country, postalCode, territory
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        String code = generateOfficeCode();
        o.setOfficeCode(code);

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, o.getOfficeCode());
            ps.setString(2, o.getCity());
            ps.setString(3, o.getPhone());
            ps.setString(4, o.getAddressLine1());
            ps.setString(5, o.getAddressLine2());
            ps.setString(6, o.getState());
            ps.setString(7, o.getCountry());
            ps.setString(8, o.getPostalCode());
            ps.setString(9, o.getTerritory());

            ps.executeUpdate();
            return o;
        } catch (SQLException e) {
            throw new RuntimeException("Insert failed", e);
        }
    }

    public Optional<Office> findById(String code) {
        String sql = "SELECT * FROM offices WHERE officeCode = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, code);
            try (ResultSet rs = ps.executeQuery()) {

                if (rs.next()) return Optional.of(mapRow(rs));

                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Find by ID failed", e);
        }
    }

    public List<Office> findAll() {
        String sql = "SELECT * FROM offices";
        List<Office> list = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) list.add(mapRow(rs));
            return list;
        } catch (SQLException e) {
            throw new RuntimeException("Find all failed", e);
        }
    }
    public boolean update(Office o) {
        String sql = """
            UPDATE offices SET
                city = ?, phone = ?, addressLine1 = ?, addressLine2 = ?,
                state = ?, country = ?, postalCode = ?, territory = ?
            WHERE officeCode = ?
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, o.getCity());
            ps.setString(2, o.getPhone());
            ps.setString(3, o.getAddressLine1());
            ps.setString(4, o.getAddressLine2());
            ps.setString(5, o.getState());
            ps.setString(6, o.getCountry());
            ps.setString(7, o.getPostalCode());
            ps.setString(8, o.getTerritory());
            ps.setString(9, o.getOfficeCode());

            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Update failed", e);
        }
    }

    /**
     * Update only the lat/lng of an existing office. Used by the
     * geocoding flow — saves a fresh-from-Nominatim coordinate without
     * touching any other field. A focused method beats reusing the
     * full {@link #update(Office)} because we'd otherwise need to
     * re-read the office, mutate one field, and write everything back.
     */
    public boolean updateCoordinates(String code, double lat, double lng) {
        String sql = "UPDATE offices SET lat = ?, lng = ? WHERE officeCode = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, lat);
            ps.setDouble(2, lng);
            ps.setString(3, code);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Update coordinates failed", e);
        }
    }

    public boolean delete(String code) {
        String sql = "DELETE FROM offices WHERE officeCode = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, code);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Delete failed", e);
        }
    }

    private Office mapRow(ResultSet rs) throws SQLException {
        var o = new Office();
        o.setOfficeCode(rs.getString("officeCode"));
        o.setCity(rs.getString("city"));
        o.setPhone(rs.getString("phone"));
        o.setAddressLine1(rs.getString("addressLine1"));
        o.setAddressLine2(rs.getString("addressLine2"));
        o.setState(rs.getString("state"));
        o.setCountry(rs.getString("country"));
        o.setPostalCode(rs.getString("postalCode"));
        o.setTerritory(rs.getString("territory"));
        // lat/lng are added by Flyway V3. They live in DECIMAL columns,
        // so JDBC hands them back as BigDecimal. Convert to boxed Double
        // so a SQL NULL stays a Java null (rs.getDouble would return 0).
        java.math.BigDecimal lat = rs.getBigDecimal("lat");
        java.math.BigDecimal lng = rs.getBigDecimal("lng");
        o.setLat(lat == null ? null : lat.doubleValue());
        o.setLng(lng == null ? null : lng.doubleValue());
        return o;
    }

    private String generateOfficeCode() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
    }

    public List<Office> findAllPaged(int page, int size, String sortBy, boolean asc) {
        if (page < 0 || size <= 0) throw new IllegalArgumentException("Invalid page/size");
        String order = asc ? "ASC" : "DESC";
        String sortColumn = switch (sortBy) {
            case "city", "country", "officeCode" -> sortBy;
            default -> "officeCode";
        };
        String sql = ("SELECT * FROM offices ORDER BY %s %s LIMIT ? OFFSET ?").formatted(sortColumn, order);

        List<Office> list = new ArrayList<>();
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, size);
            ps.setInt(2, page * size);
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) list.add(mapRow(rs)); }
        } catch (SQLException e) { throw new RuntimeException("Find paged failed", e); }
        return list;
    }

    public long countAll() {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM offices");
             ResultSet rs = ps.executeQuery()) {
            rs.next(); return rs.getLong(1);
        } catch (SQLException e) { throw new RuntimeException("Count failed", e); }
    }

    public int saveAll(List<Office> offices) {
        final String sql = """
        INSERT INTO offices (
            officeCode, city, phone, addressLine1, addressLine2,
            state, country, postalCode, territory
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            int batch = 0;
            for (Office o : offices) {
                ps.setString(1, o.getOfficeCode());
                ps.setString(2, o.getCity());
                ps.setString(3, o.getPhone());
                ps.setString(4, o.getAddressLine1());
                ps.setString(5, o.getAddressLine2());
                ps.setString(6, o.getState());
                ps.setString(7, o.getCountry());
                ps.setString(8, o.getPostalCode());
                ps.setString(9, o.getTerritory());
                ps.addBatch();

                if (++batch % 500 == 0) ps.executeBatch();
            }
            ps.executeBatch();
            return batch;
        } catch (SQLException e) {
            throw new RuntimeException("Batch insert (offices) failed", e);
        }
    }
}
