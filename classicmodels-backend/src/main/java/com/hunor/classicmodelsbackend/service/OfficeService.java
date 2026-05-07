package com.hunor.classicmodelsbackend.service;

import com.hunor.classicmodelsbackend.dto.office.OfficeRequestDTO;
import com.hunor.classicmodelsbackend.dto.office.OfficeResponseDTO;
import com.hunor.classicmodelsbackend.geocoding.GeocodingService;
import com.hunor.classicmodelsbackend.mapper.OfficeMapper;
import com.hunor.classicmodelsbackend.model.Office;
import com.hunor.classicmodelsbackend.repository.OfficeRepository;
import com.hunor.classicmodelsbackend.response.PageResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class OfficeService {

    private final OfficeRepository repo;
    private final OfficeMapper mapper;
    private final GeocodingService geocoding;

    public OfficeService(OfficeRepository repo,
                         OfficeMapper mapper,
                         GeocodingService geocoding) {
        this.repo = repo;
        this.mapper = mapper;
        this.geocoding = geocoding;
    }

    @Cacheable(cacheNames = "officesAll")
    public List<OfficeResponseDTO> findAll() {
        log.info("Finding all offices");
        long startTime = System.currentTimeMillis();

        var result= repo.findAll().stream().map(mapper::toResponseDTO).toList();

        long duration = System.currentTimeMillis() - startTime;
        log.info("Found {} offices in {} ms", result.size(), duration);

        return result;
    }

    @Cacheable(cacheNames = "offices", key = "#code")
    public OfficeResponseDTO findById(String code) {
        log.info("Finding an office by code: {}", code);
        long startTime = System.currentTimeMillis();

        var result = repo.findById(code)
                .map(mapper::toResponseDTO)
                .orElseThrow(() -> new RuntimeException("Office " + code + " not found"));

        long duration = System.currentTimeMillis() - startTime;
        log.info("Found an office by ID {} in {} ms", code, duration);

        return result;
    }

    @CachePut(cacheNames = "offices", key = "#result.officeCode()")
    @CacheEvict(cacheNames = "officesAll", allEntries = true)
    public OfficeResponseDTO create(OfficeRequestDTO dto) {
        log.info("Creating an office: {}", dto.city());
        long startTime = System.currentTimeMillis();

        if (dto.city() == null || dto.city().trim().isEmpty()) {
            throw new RuntimeException("City is required");
        }
        if (dto.country() == null || dto.country().trim().isEmpty()) {
            throw new RuntimeException("Country is required");
        }
        var entity = mapper.toEntity(dto);
        var saved = repo.save(entity);
        var responseDTO = mapper.toResponseDTO(saved);

        long duration = System.currentTimeMillis() - startTime;
        log.info("Created office {} in {} ms", responseDTO.city(), duration);

        return responseDTO;
    }

    @CachePut(cacheNames = "offices", key = "#code")
    @CacheEvict(cacheNames = "officesAll", allEntries = true)
    public OfficeResponseDTO update(String code, OfficeRequestDTO dto) {
        log.info("Updating office {}", code);
        long startTime = System.currentTimeMillis();

        if (dto.city() == null || dto.city().trim().isEmpty()) {
            throw new RuntimeException("City is required");
        }
        if (dto.country() == null || dto.country().trim().isEmpty()) {
            throw new RuntimeException("Country is required");
        }
        var existing = repo.findById(code)
                .orElseThrow(() -> new RuntimeException("Office " + code + " not found"));
        mapper.copyToEntity(dto, existing);
        repo.update(existing);
        var response = mapper.toResponseDTO(existing);

        long duration = System.currentTimeMillis() - startTime;
        log.info("Updated office {} in {} ms", code, duration);
        return response;
    }

    /**
     * Geocode an office's address via Nominatim and persist the
     * resulting lat/lng.
     *
     * <h4>Why a cascade of queries?</h4>
     *
     * <p>Real-world geocoding rarely succeeds on the first try with the
     * full address. Different countries format addresses in different
     * orders (Japan: ward → city → country; UK: street → town → county
     * → country); typos, abbreviations, and unfamiliar street names
     * also throw off the matcher. The standard mitigation is a
     * <em>fallback chain</em>: most-specific query first, less-specific
     * if that fails. Almost every geocoder can find "Tokyo, Japan"
     * even when it can't find "4-1 Kioicho, Chiyoda-Ku 102-8578."</p>
     *
     * <p>Our chain has three rungs:
     * <ol>
     *   <li>Full address (best precision when it works)</li>
     *   <li>City + country (always succeeds for any reasonable city)</li>
     *   <li>Country alone (last-ditch fallback to the country centroid)</li>
     * </ol>
     * The first match wins. If even the country fails, we throw —
     * that means Nominatim is unreachable, not that the data is bad.</p>
     */
    @CacheEvict(cacheNames = {"offices", "officesAll"}, allEntries = true)
    public OfficeResponseDTO geocode(String code) {
        log.info("Geocoding office {}", code);

        Office office = repo.findById(code)
                .orElseThrow(() -> new RuntimeException("Office " + code + " not found"));

        var result = geocodeWithFallbacks(office);

        repo.updateCoordinates(code, result.lat(), result.lng());

        Office updated = repo.findById(code).orElseThrow();
        return mapper.toResponseDTO(updated);
    }

    /**
     * Try a sequence of progressively-less-specific queries; return the
     * first successful match.
     */
    private GeocodingService.Coordinates geocodeWithFallbacks(Office office) {
        List<String> queries = buildFallbackQueries(office);

        for (String query : queries) {
            log.info("Trying geocode query: '{}'", query);
            var attempt = geocoding.geocode(query);
            if (attempt.isPresent()) {
                log.info("Match found via '{}'", query);
                return attempt.get();
            }
        }

        throw new RuntimeException(
                "Geocoding returned no result for any of these queries: " + queries);
    }

    /**
     * Build the fallback chain for an office, most-specific first.
     */
    private List<String> buildFallbackQueries(Office o) {
        List<String> queries = new java.util.ArrayList<>();

        // 1. Full address — best precision when it parses.
        String full = buildAddressQuery(o);
        if (!full.isBlank()) queries.add(full);

        // 2. City + country — robust fallback. The geocoder will land
        //    on the city centre, which is good enough for "where in
        //    the world is this office" purposes.
        String cityCountry = compose(o.getCity(), o.getCountry());
        if (!cityCountry.isBlank() && !cityCountry.equals(full)) {
            queries.add(cityCountry);
        }

        // 3. Country alone — last-ditch fallback. Returns the country
        //    centroid, which is too coarse for most uses but better
        //    than failing entirely.
        if (o.getCountry() != null && !o.getCountry().isBlank()
                && !queries.contains(o.getCountry())) {
            queries.add(o.getCountry());
        }
        return queries;
    }

    /**
     * Compose a Nominatim-friendly query from the office's address.
     * Format: {@code addressLine1, addressLine2?, city, state? postalCode, country}.
     */
    private String buildAddressQuery(Office o) {
        StringBuilder sb = new StringBuilder();
        appendIfPresent(sb, o.getAddressLine1());
        appendIfPresent(sb, o.getAddressLine2());
        appendIfPresent(sb, o.getCity());
        String stateAndZip = (o.getState() != null ? o.getState() + " " : "")
                + (o.getPostalCode() != null ? o.getPostalCode() : "");
        appendIfPresent(sb, stateAndZip.trim());
        appendIfPresent(sb, o.getCountry());
        return sb.toString();
    }

    /** Comma-join non-blank pieces; useful for ad-hoc query composition. */
    private String compose(String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) appendIfPresent(sb, p);
        return sb.toString();
    }

    private void appendIfPresent(StringBuilder sb, String value) {
        if (value == null || value.isBlank()) return;
        if (!sb.isEmpty()) sb.append(", ");
        sb.append(value);
    }

    @CacheEvict(cacheNames = {"offices", "officesAll"}, allEntries = true)
    public void delete(String code) {
        log.info("Deleting office {}", code);
        long startTime = System.currentTimeMillis();

        repo.findById(code)
                .orElseThrow(() -> new RuntimeException("Office " + code + " not found"));
        repo.delete(code);

        long duration = System.currentTimeMillis() - startTime;
        log.info("Deleted office {} in {} ms", code, duration);
    }

    public PageResponse<OfficeResponseDTO> findAllPaged(int page, int size, String sortBy, boolean asc) {
        log.info("Finding offices page={} size={} sortBy={} asc={}", page, size, sortBy, asc);
        long startTime = System.currentTimeMillis();

        var items = repo.findAllPaged(page, size, sortBy, asc).stream().map(mapper::toResponseDTO).toList();
        long total = repo.countAll();
        int totalPages = (int) Math.ceil((double) total / size);
        var response = new PageResponse<>(items, page, size, total, totalPages);

        long duration = System.currentTimeMillis() - startTime;
        log.info("Found {} offices (page {}) in {} ms", items.size(), page, duration);
        return response;
    }

    @CacheEvict(cacheNames = "officesAll", allEntries = true)
    public void createBulk(List<OfficeRequestDTO> dtos) {
        log.info("Creating {} offices in bulk", dtos.size());
        long startTime = System.currentTimeMillis();

        var entities = dtos.stream().map(mapper::toEntity).toList();
        repo.saveAll(entities);

        long duration = System.currentTimeMillis() - startTime;
        log.info("Bulk created {} offices in {} ms", entities.size(), duration);
    }
}
