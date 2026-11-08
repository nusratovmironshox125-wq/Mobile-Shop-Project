package sayt.controller;

import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import sayt.dto.catalog.ImageView;
import sayt.security.ShopPrincipal;
import sayt.service.MediaService;

@RestController
class MediaController {
    private final MediaService media;
    MediaController(MediaService media) { this.media = media; }

    @GetMapping({"/media/{key}", "/api/v1/admin/media/{key}"})
    ResponseEntity<Resource> get(@PathVariable UUID key, jakarta.servlet.http.HttpServletRequest request) {
        return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).cacheControl(CacheControl.noStore())
            .body(media.get(key, request.getServletPath().startsWith("/api/")));
    }

    @PostMapping("/api/v1/admin/products/{id}/images")
    @ResponseStatus(HttpStatus.CREATED)
    ImageView upload(@PathVariable long id, @RequestParam MultipartFile file,
        @RequestParam(required=false) Long variantId, @RequestParam(required=false) @Size(max=200) String alt,
        @AuthenticationPrincipal ShopPrincipal actor) throws IOException {
        try (var content = file.getInputStream()) {
            return media.upload(id, variantId, alt, content, file.getSize(), actor.id());
        }
    }

    @DeleteMapping("/api/v1/admin/products/{id}/images/{imageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable long id, @PathVariable long imageId, @AuthenticationPrincipal ShopPrincipal actor) {
        media.delete(id, imageId, actor.id());
    }
}
