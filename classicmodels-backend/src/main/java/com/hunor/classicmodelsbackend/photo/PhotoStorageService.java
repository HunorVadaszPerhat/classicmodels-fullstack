package com.hunor.classicmodelsbackend.photo;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Optional;

/**
 * Filesystem-backed photo storage for employees.
 *
 * <h3>Why filesystem and not the database?</h3>
 *
 * <p>Three reasons:</p>
 * <ul>
 *   <li><b>Backups stay sane.</b> A row's worth of metadata is small;
 *       a megabyte of photo data per row would balloon DB backups.</li>
 *   <li><b>Streaming.</b> The HTTP response can stream straight from
 *       a file rather than loading bytes into memory.</li>
 *   <li><b>Simplicity.</b> No new schema. The existence of the file
 *       at the conventional path IS the metadata.</li>
 * </ul>
 *
 * <p>Trade-off: in a multi-server deployment, the filesystem is local
 * to one machine. Production-grade setups put photos in object storage
 * (S3, MinIO, GCS) and reference them by key. The {@code Optional<Path>}
 * return shape from {@link #find} is deliberately abstract enough that
 * a future S3 implementation could replace this one without touching
 * controller code.</p>
 *
 * <h3>Path layout</h3>
 *
 * <pre>
 *   {uploadsDir}/employee-photos/{employeeId}.{ext}
 *   ─────────────  ────────────  ────────────  ────
 *   configurable   subdirectory  numeric id    jpg | png
 * </pre>
 *
 * <p>The numeric id is the employeeNumber. The extension comes from the
 * upload's content-type — we accept JPEG and PNG; everything else is
 * rejected. {@link #find} doesn't know which extension was used so it
 * tries both.</p>
 */
@Service
@Slf4j
public class PhotoStorageService {

    private static final String SUBDIR = "employee-photos";
    private static final List<String> ALLOWED_EXTENSIONS = List.of("jpg", "png");

    private final Path baseDir;
    private final com.hunor.classicmodelsbackend.metrics.AppMetrics metrics;

    public PhotoStorageService(@Value("${app.uploads.dir}") String uploadsDir,
                               com.hunor.classicmodelsbackend.metrics.AppMetrics metrics) {
        this.baseDir = Path.of(uploadsDir, SUBDIR);
        this.metrics = metrics;
    }

    /**
     * Create the storage directory if it doesn't exist. Runs after the
     * service is constructed and DI is complete. Failing here is fine —
     * Spring will surface the error and the app won't start, which is
     * what we want when the configured directory is unwriteable.
     */
    @PostConstruct
    void init() throws IOException {
        Files.createDirectories(baseDir);
        log.info("Employee-photo storage initialised at {}", baseDir.toAbsolutePath());
    }

    /**
     * Save a photo for an employee. Replaces any existing photo for
     * that employee — there's only ever one current photo per id.
     *
     * @return the extension of the stored file (jpg or png)
     */
    public String save(int employeeId, MultipartFile file) throws IOException {
        // Time the entire save operation. Timer.recordCallable wraps
        // a Callable, records duration on success or failure, and
        // re-throws checked exceptions (we re-throw IOException for
        // the controller to handle). Result lands in
        // /actuator/prometheus as classicmodels_photo_upload_duration_seconds_*.
        try {
            return metrics.photoUploadTimer().recordCallable(() -> {
                String ext = extensionFor(file);
                // Clean up any previous photo (might be at a different
                // extension than this upload). The "one photo per id" invariant
                // is enforced by deleting before writing.
                delete(employeeId);

                Path target = baseDir.resolve(employeeId + "." + ext);
                // REPLACE_EXISTING is belt-and-suspenders: delete() above
                // already removed it, but the flag handles a race where two
                // uploads arrive for the same id at exactly the same time.
                Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
                log.info("Saved {} bytes for employee {} as {}", file.getSize(), employeeId, target);
                return ext;
            });
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            // Timer.recordCallable wraps in Exception; restore the IOException
            // type for callers that catch it specifically. Anything else is
            // a programming bug — wrap as IOException so the signature stays
            // honest.
            throw new IOException("Photo save failed", e);
        }
    }

    /**
     * Find the photo file for an employee, if one exists. Tries each
     * allowed extension in turn — we don't track which one was used, so
     * a missing file just means "no photo," not an error.
     */
    public Optional<Path> find(int employeeId) {
        for (String ext : ALLOWED_EXTENSIONS) {
            Path candidate = baseDir.resolve(employeeId + "." + ext);
            if (Files.exists(candidate)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    /**
     * Delete every photo file associated with this employee, regardless
     * of extension. Idempotent — a missing file is silent.
     */
    public void delete(int employeeId) {
        for (String ext : ALLOWED_EXTENSIONS) {
            Path candidate = baseDir.resolve(employeeId + "." + ext);
            try {
                Files.deleteIfExists(candidate);
            } catch (IOException e) {
                // Logged but not propagated — a leaked file is annoying
                // but not a correctness bug. Log so the operator can
                // spot it during housekeeping.
                log.warn("Failed to delete {}: {}", candidate, e.getMessage());
            }
        }
    }

    /**
     * Map a multipart's content-type to one of our allowed extensions.
     * Throws on anything we don't accept.
     *
     * <p>For paranoid security you'd also "sniff" the file's magic
     * bytes to verify it really is the type the browser claims (so
     * an attacker can't upload a script with image/jpeg headers).
     * For learning, content-type is fine.</p>
     */
    private String extensionFor(MultipartFile file) {
        String type = file.getContentType();
        if (type == null) {
            throw new IllegalArgumentException("Missing content type on upload");
        }
        return switch (type.toLowerCase()) {
            case "image/jpeg", "image/jpg" -> "jpg";
            case "image/png" -> "png";
            default -> throw new IllegalArgumentException(
                    "Unsupported image type: " + type + " (allowed: image/jpeg, image/png)");
        };
    }

    /**
     * Best-effort guess of the content-type from a stored file's
     * extension. Used when serving photos back so the browser knows
     * how to render them.
     */
    public String contentTypeOf(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        if (name.endsWith(".png")) return "image/png";
        return "image/jpeg";  // default for .jpg/.jpeg
    }
}
