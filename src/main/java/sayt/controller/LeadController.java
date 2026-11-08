package sayt.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import sayt.dto.lead.*;
import sayt.service.LeadService;

@RestController
@RequestMapping("/api/v1")
class LeadController {
    private final LeadService leads;
    LeadController(LeadService leads) { this.leads = leads; }

    @PostMapping("/leads")
    @ResponseStatus(HttpStatus.CREATED)
    LeadReceipt create(@Valid @RequestBody CreateLeadRequest input, @RequestHeader("Idempotency-Key") UUID key,
        HttpServletRequest request) {
        return leads.create(input, key, request.getSession().getId(), request.getRemoteAddr());
    }
}
