package sayt.controller.admin;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import sayt.dto.notification.NotificationQueueResponse;
import sayt.security.ShopPrincipal;
import sayt.service.NotificationService;

@RestController
@RequestMapping("/api/v1/admin/notifications")
class AdminNotificationController {
    private final NotificationService outbox;
    AdminNotificationController(NotificationService outbox) { this.outbox = outbox; }

    @GetMapping
    NotificationQueueResponse list(@RequestParam(defaultValue="0") @jakarta.validation.constraints.Min(0)
        @jakarta.validation.constraints.Max(10000) int page) { return outbox.list(page); }

    @PostMapping("/{id}/retry")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void retry(@PathVariable long id, @AuthenticationPrincipal ShopPrincipal actor) {
        outbox.retry(id, actor.id());
    }
}
