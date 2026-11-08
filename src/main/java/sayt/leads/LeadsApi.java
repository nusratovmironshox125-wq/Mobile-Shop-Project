package sayt.leads;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import sayt.identity.ShopPrincipal;

@RestController
@RequestMapping("/api/v1")
class LeadsApi {
    private final Leads leads;
    LeadsApi(Leads leads) { this.leads = leads; }

    @PostMapping("/leads")
    @ResponseStatus(HttpStatus.CREATED)
    Leads.Receipt create(@Valid @RequestBody Leads.Input input, @RequestHeader("Idempotency-Key") UUID key,
        HttpServletRequest request) {
        return leads.create(input, key, request.getSession().getId(), request.getRemoteAddr());
    }

    @GetMapping("/admin/leads")
    Leads.ListView list(@RequestParam(required=false) @Pattern(regexp="NEW|CONTACTED|COMPLETED|CANCELLED") String status,
        @RequestParam(required=false) @Size(max=80) String q,
        @RequestParam(required=false) LocalDate from, @RequestParam(required=false) LocalDate until,
        @RequestParam(defaultValue="0") @Min(0) @Max(10000) int page) {
        return leads.list(status, q, from, until, page);
    }

    @GetMapping("/admin/leads/{id}")
    Leads.Detail detail(@PathVariable long id) { return leads.detail(id); }

    @PatchMapping("/admin/leads/{id}")
    Leads.Detail update(@PathVariable long id, @Valid @RequestBody Leads.Update update,
        @AuthenticationPrincipal ShopPrincipal actor) {
        return leads.update(id, update, actor.id(), "OWNER".equals(actor.role()));
    }

    @PostMapping("/admin/leads/{id}/anonymize")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void anonymize(@PathVariable long id, @AuthenticationPrincipal ShopPrincipal actor) {
        leads.anonymize(id, actor.id());
    }
}
