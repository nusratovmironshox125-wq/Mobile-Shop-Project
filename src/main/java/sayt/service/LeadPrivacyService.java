package sayt.service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sayt.exception.Problem;
import sayt.repository.LeadRepository;

@Service
public class LeadPrivacyService {
    private final LeadRepository leads;
    private final NotificationService notifications;
    private final AuditService audit;
    private final Clock clock;
    private final int retentionDays;

    LeadPrivacyService(LeadRepository leads, NotificationService notifications, AuditService audit, Clock clock,
                       @Value("${shop.retention-days}") int retentionDays) {
        this.leads = leads;
        this.notifications = notifications;
        this.audit = audit;
        this.clock = clock;
        this.retentionDays = Math.max(1, retentionDays);
    }

    public OffsetDateTime cutoff() { return OffsetDateTime.now(clock).minusDays(retentionDays); }

    public List<Long> expiredIds(OffsetDateTime cutoff) { return leads.expiredIds(cutoff); }

    @Transactional
    public void anonymize(long id, long actor) {
        leads.lock(id, cutoff()).orElseThrow(Problem::missing);
        anonymizeLocked(id, actor);
    }

    @Transactional
    public void anonymizeExpired(long id, OffsetDateTime cutoff) {
        leads.lock(id, cutoff).filter(lead -> lead.anonymizedAt() == null && lead.closedAt() != null
            && lead.closedAt().isBefore(cutoff)).ifPresent(lead -> anonymizeLocked(lead.id(), null));
    }

    private void anonymizeLocked(long id, Long actor) {
        notifications.cancelForLead(id);
        if (leads.anonymize(id)) audit.record(actor, "LEAD_ANONYMIZED", "lead:" + id);
    }
}
