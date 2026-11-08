package sayt.service;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import javax.imageio.ImageIO;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;
import sayt.dto.catalog.ImageView;
import sayt.entity.ProductImage;
import sayt.exception.Problem;
import sayt.mapper.CatalogMapper;
import sayt.repository.*;

@Service
public class MediaService {
    private final ProductImageRepository images;
    private final ProductRepository products;
    private final VariantRepository variants;
    private final AuditService audit;
    private final ImageStorage storage;
    private final TransactionTemplate transactions;
    private final Semaphore conversions = new Semaphore(2);

    MediaService(ProductImageRepository images, ProductRepository products, VariantRepository variants,
                 AuditService audit, ImageStorage storage, PlatformTransactionManager transactionManager) {
        this.images = images;
        this.products = products;
        this.variants = variants;
        this.audit = audit;
        this.storage = storage;
        transactions = new TransactionTemplate(transactionManager);
    }

    public Resource get(UUID key, boolean admin) {
        if (!images.visible(key, admin)) throw Problem.missing();
        return storage.read(key).orElseThrow(Problem::missing);
    }

    public ImageView upload(long id, Long variantId, String alt, InputStream content, long size, long actor) {
        if (!conversions.tryAcquire()) throw Problem.limited(5);
        BufferedImage decoded;
        try {
            decoded = decode(content, size);
        } finally {
            conversions.release();
        }
        return transactions.execute(status -> save(id, variantId, alt, decoded, actor));
    }

    private ImageView save(long id, Long variantId, String alt, BufferedImage decoded, long actor) {
        products.lockStatus(id).orElseThrow(Problem::missing);
        if (images.countByProductId(id) >= 8) throw Problem.bad("IMAGE_LIMIT");
        if (variantId != null && variants.findById(variantId).filter(v -> v.getProductId() == id).isEmpty()) {
            throw Problem.missing();
        }
        var image = new ProductImage();
        image.setProductId(id);
        image.setVariantId(variantId);
        image.setAlt(alt);
        image.setWidth(decoded.getWidth());
        image.setHeight(decoded.getHeight());
        image.setStorageKey(UUID.randomUUID());
        var key = image.getStorageKey();
        try {
            storage.write(key, decoded);
            afterCompletion(key, false);
        } catch (IOException error) {
            throw new Problem(HttpStatus.SERVICE_UNAVAILABLE, "MEDIA_UNAVAILABLE", Map.of("retryAfter", 5));
        }
        images.saveAndFlush(image);
        audit.record(actor, "IMAGE_ADDED", "image:" + image.getId());
        return CatalogMapper.imageView(image);
    }

    @Transactional
    public void delete(long id, long imageId, long actor) {
        String status = products.lockStatus(id).orElseThrow(Problem::missing);
        var image = images.findById(imageId).filter(i -> i.getProductId() == id).orElseThrow(Problem::missing);
        if ("PUBLISHED".equals(status) && images.countByProductId(id) <= 1) {
            throw Problem.bad("PUBLICATION_REQUIREMENTS");
        }
        images.delete(image);
        afterCompletion(image.getStorageKey(), true);
        audit.record(actor, "IMAGE_REMOVED", "image:" + imageId);
    }

    private void afterCompletion(UUID key, boolean committed) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if ((status == STATUS_COMMITTED) != committed || status == STATUS_UNKNOWN) return;
                try {
                    storage.delete(key);
                } catch (IOException error) {
                    System.getLogger(MediaService.class.getName()).log(System.Logger.Level.WARNING, "Media cleanup failed");
                }
            }
        });
    }

    private static BufferedImage decode(InputStream content, long size) {
        if (size == 0 || size > 5 * 1024 * 1024) throw Problem.bad("IMAGE_INVALID");
        try (var input = ImageIO.createImageInputStream(content)) {
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
