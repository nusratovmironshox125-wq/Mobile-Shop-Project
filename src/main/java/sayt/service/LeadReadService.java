package sayt.service;

import java.time.Duration;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sayt.dto.lead.LeadUpdates;
import sayt.repository.LeadReadRepository;

@Service
public class LeadReadService {
    private final LeadReadRepository repository;
    private final LeadService leads;
    private final RateLimits limits;
    public LeadReadService(LeadReadRepository repository, LeadService leads, RateLimits limits) {
        this.repository=repository; this.leads=leads; this.limits=limits;
    }
    @Transactional(readOnly=true, isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public LeadUpdates updates(long actor) {
        limits.acquire("lead-updates:"+actor, 120, Duration.ofMinutes(1));
        return repository.updates(actor);
    }
    @Transactional
    public void mark(long id, long actor) {
        limits.acquire("lead-read:"+actor, 120, Duration.ofMinutes(1));
        leads.detail(id);
        repository.mark(actor,id);
    }
}
