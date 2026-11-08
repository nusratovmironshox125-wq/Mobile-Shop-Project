package sayt.controller.admin;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import sayt.security.ShopPrincipal;
import sayt.dto.lead.*;
import sayt.service.LeadService;
import sayt.service.LeadPrivacyService;

@RestController
@RequestMapping("/api/v1")
class AdminLeadController {
    private final LeadService leads;
    private final LeadPrivacyService privacy;

    AdminLeadController(LeadService leads, LeadPrivacyService privacy) {
        this.leads = leads;
        this.privacy = privacy;
    }

    @GetMapping("/admin/leads")
    LeadListResponse list(@RequestParam(required=false) @Pattern(regexp="NEW|CONTACTED|COMPLETED|CANCELLED") String status,
        @RequestParam(required=false) @Size(max=80) String q,
        @RequestParam(required=false) LocalDate from, @RequestParam(required=false) LocalDate until,
        @RequestParam(defaultValue="0") @Min(0) @Max(10000) int page) {
        return leads.list(status, q, from, until, page);
    }

    @GetMapping("/admin/leads/{id}")
    LeadDetailResponse detail(@PathVariable long id) { return leads.detail(id); }

    @PatchMapping("/admin/leads/{id}")
    LeadDetailResponse update(@PathVariable long id, @Valid @RequestBody UpdateLeadRequest update,
        @AuthenticationPrincipal ShopPrincipal actor) {
        return leads.update(id, update, actor.id(), "OWNER".equals(actor.role()));
    }

    @PostMapping("/admin/leads/{id}/anonymize")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void anonymize(@PathVariable long id, @AuthenticationPrincipal ShopPrincipal actor) {
        privacy.anonymize(id, actor.id());
    }
}
