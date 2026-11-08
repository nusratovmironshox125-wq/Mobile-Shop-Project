package sayt.catalog;

import jakarta.validation.constraints.Size;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.*;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import javax.imageio.ImageIO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import sayt.common.*;
import sayt.identity.ShopPrincipal;

@RestController
class MediaApi {
    private final Media media;
    MediaApi(Media media) { this.media = media; }

    @GetMapping({"/media/{key}", "/api/v1/admin/media/{key}"})
    ResponseEntity<FileSystemResource> get(@PathVariable UUID key, jakarta.servlet.http.HttpServletRequest request) {
        return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).cacheControl(CacheControl.noStore())
            .body(media.get(key, request.getServletPath().startsWith("/api/")));
    }

    @PostMapping("/api/v1/admin/products/{id}/images")
    @ResponseStatus(HttpStatus.CREATED)
    Catalog.ImageView upload(@PathVariable long id, @RequestParam MultipartFile file,
        @RequestParam(required=false) Long variantId, @RequestParam(required=false) @Size(max=200) String alt,
        @AuthenticationPrincipal ShopPrincipal actor) {
        return media.upload(id, variantId, alt, file, actor.id());
    }

    @DeleteMapping("/api/v1/admin/products/{id}/images/{imageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable long id, @PathVariable long imageId, @AuthenticationPrincipal ShopPrincipal actor) {
        media.delete(id, imageId, actor.id());
    }
}

@Service
class Media {
    private final Path directory;
    private final ProductImages images;
    private final Variants variants;
    private final JdbcClient db;
    private final Audit audit;
    private final Semaphore conversions = new Semaphore(2);

    Media(@Value("${shop.media-dir}") Path directory, ProductImages images, Variants variants,
          JdbcClient db, Audit audit) {
        this.directory = directory.toAbsolutePath().normalize();
        this.images = images;
        this.variants = variants;
        this.db = db;
        this.audit = audit;
    }

    FileSystemResource get(UUID key, boolean admin) {
        var sql = """
            select exists(select 1 from product_image i join product p on i.product_id=p.id
              join category c on c.id=p.category_id where i.storage_key=?
            """ + (admin ? ")" : " and p.status='PUBLISHED' and c.enabled)");
        Path file = path(key);
        if (!db.sql(sql).param(key).query(Boolean.class).single() || !Files.isRegularFile(file)) throw Problem.missing();
        return new FileSystemResource(file);
    }

    @Transactional
    Catalog.ImageView upload(long id, Long variantId, String alt, MultipartFile file, long actor) {
        if (!conversions.tryAcquire()) throw Problem.limited(5);
        BufferedImage decoded;
        try {
            decoded = decode(file);
        } finally {
            conversions.release();
        }
        lockProduct(id);
        if (images.countByProductId(id) >= 8) throw Problem.bad("IMAGE_LIMIT");
        if (variantId != null && variants.findById(variantId).filter(v -> v.productId == id).isEmpty()) {
            throw Problem.missing();
        }
        var image = new ProductImage();
        image.productId = id;
        image.variantId = variantId;
        image.alt = alt;
        image.width = decoded.getWidth();
        image.height = decoded.getHeight();
        image.storageKey = UUID.randomUUID();
        Path target = path(image.storageKey);
        afterCompletion(target, false);
        try {
            Files.createDirectories(directory);
            try (var output = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW)) {
                if (!ImageIO.write(decoded, "jpeg", output)) throw new IOException("JPEG writer unavailable");
            }
        } catch (IOException error) {
            throw new Problem(HttpStatus.SERVICE_UNAVAILABLE, "MEDIA_UNAVAILABLE", Map.of("retryAfter", 5));
        }
        images.saveAndFlush(image);
        audit.record(actor, "IMAGE_ADDED", "image:" + image.id);
        return Catalog.imageView(image);
    }

    @Transactional
    void delete(long id, long imageId, long actor) {
        String status = lockProduct(id);
        var image = images.findById(imageId).filter(i -> i.productId == id).orElseThrow(Problem::missing);
        if ("PUBLISHED".equals(status) && images.countByProductId(id) <= 1) {
            throw Problem.bad("PUBLICATION_REQUIREMENTS");
        }
        images.delete(image);
        afterCompletion(path(image.storageKey), true);
        audit.record(actor, "IMAGE_REMOVED", "image:" + imageId);
    }

    private String lockProduct(long id) {
        return db.sql("select status from product where id=? for update").param(id)
            .query(String.class).optional().orElseThrow(Problem::missing);
    }

    private Path path(UUID key) { return directory.resolve(key + ".jpg"); }

    private void afterCompletion(Path path, boolean committed) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if ((status == STATUS_COMMITTED) != committed || status == STATUS_UNKNOWN) return;
                try {
                    Files.deleteIfExists(path);
                } catch (IOException error) {
                    System.getLogger(Media.class.getName()).log(System.Logger.Level.WARNING, "Media cleanup failed");
                }
            }
        });
    }

    private static BufferedImage decode(MultipartFile file) {
        if (file.isEmpty() || file.getSize() > 5 * 1024 * 1024) throw Problem.bad("IMAGE_INVALID");
        try (var bytes = file.getInputStream(); var input = ImageIO.createImageInputStream(bytes)) {
            if (input == null) throw Problem.bad("IMAGE_INVALID");
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw Problem.bad("IMAGE_INVALID");
            var reader = readers.next();
            try {
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!format.equals("jpeg") && !format.equals("png") && !format.equals("webp")) {
                    throw Problem.bad("IMAGE_INVALID");
                }
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width < 1 || height < 1 || (long) width * height > 25_000_000) {
                    throw Problem.bad("IMAGE_DIMENSIONS");
                }
                double scale = Math.min(1, 1600.0 / Math.max(width, height));
                var result = new BufferedImage(Math.max(1, (int) (width * scale)),
                    Math.max(1, (int) (height * scale)), BufferedImage.TYPE_INT_RGB);
                Graphics2D graphics = result.createGraphics();
                try {
                    graphics.setColor(java.awt.Color.WHITE);
                    graphics.fillRect(0, 0, result.getWidth(), result.getHeight());
                    graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                    graphics.drawImage(reader.read(0), 0, 0, result.getWidth(), result.getHeight(), null);
                } finally {
                    graphics.dispose();
                }
                return result;
            } finally {
                reader.dispose();
            }
        } catch (IOException | IllegalArgumentException error) {
            throw Problem.bad("IMAGE_INVALID");
        }
    }
}
