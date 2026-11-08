package sayt.integration.media;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.*;
import java.util.Optional;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import sayt.service.ImageStorage;

@Component
class FileImageStorage implements ImageStorage {
    private final Path directory;

    FileImageStorage(@Value("${shop.media-dir}") Path directory) {
        this.directory = directory.toAbsolutePath().normalize();
    }

    @Override
    public Optional<Resource> read(UUID key) {
        Path file = path(key);
        return Files.isRegularFile(file) ? Optional.of(new FileSystemResource(file)) : Optional.empty();
    }

    @Override
    public void write(UUID key, BufferedImage image) throws IOException {
        Files.createDirectories(directory);
        Path file = path(key);
        boolean created = false;
        try (var output = Files.newOutputStream(file, StandardOpenOption.CREATE_NEW)) {
            created = true;
            if (!ImageIO.write(image, "jpeg", output)) throw new IOException("JPEG writer unavailable");
        } catch (IOException error) {
            if (created) {
                try { Files.deleteIfExists(file); }
                catch (IOException cleanup) { error.addSuppressed(cleanup); }
            }
            throw error;
        }
    }

    @Override
    public void delete(UUID key) throws IOException { Files.deleteIfExists(path(key)); }

    private Path path(UUID key) { return directory.resolve(key + ".jpg"); }
}
