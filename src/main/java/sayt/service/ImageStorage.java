package sayt.service;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.core.io.Resource;

public interface ImageStorage {
    Optional<Resource> read(UUID key);
    void write(UUID key, BufferedImage image) throws IOException;
    void delete(UUID key) throws IOException;
}
