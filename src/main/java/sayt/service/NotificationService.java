package sayt.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import sayt.dto.notification.NotificationQueueResponse;
import sayt.exception.Problem;
import sayt.repository.OutboxRepository;
import sayt.repository.OutboxRepository.Job;

@Service
public class NotificationService {
    private static final int[] BACKOFF = {60, 300, 900, 3600, 21600, 21600};
    private final OutboxRepository repository;
    private final AuditService audit;
    private final NotificationChannel channel;

    public NotificationService(OutboxRepository repository, AuditService audit, NotificationChannel channel) {
        this.repository = repository;
        this.audit = audit;
        this.channel = channel;
    }

    public NotificationQueueResponse list(int page) {
        long total=repository.count();
        return new NotificationQueueResponse(channel.enabled(), repository.list(page),page,10,total,(int)((total+9)/10),(page+1L)*10<total);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(long leadId) {
        if (channel.enabled()) repository.enqueue(leadId);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void cancelForLead(long leadId) {
        if (repository.leaseActive(leadId)) throw Problem.conflict("NOTIFICATION_IN_PROGRESS");
        repository.cancelForLead(leadId);
    }

    @Transactional
    public void retry(long id, long actor) {
        if (!channel.enabled()) throw Problem.conflict("NOTIFICATIONS_DISABLED");
        int updated = repository.retry(id);
        if (updated != 1) throw Problem.conflict("NOTIFICATION_NOT_FAILED");
        audit.record(actor, "NOTIFICATION_RETRY", "notification:" + id);
    }

    public void drain() {
        if (!channel.enabled()) return;
        for (int count = 0; count < 8; count++) {
            var job = repository.claim();
            if (job.isEmpty()) return;
            deliver(job.get());
        }
    }

    private void deliver(Job job) {
        var message = repository.message(job.leadId());
        if (message.isEmpty()) {
            repository.finish(job, "CANCELLED", 0, null);
            return;
        }
        NotificationChannel.Delivery result;
        try { result = channel.send(message.get()); }
        catch (RuntimeException failure) { result = new NotificationChannel.Delivery(false,"CHANNEL_UNAVAILABLE",0); }
        int attempts = job.attempts() + 1;
        if (result.sent()) repository.finish(job, "SENT", 0, null);
        else if (attempts > BACKOFF.length) repository.finish(job, "FAILED", 0, result.error());
        else repository.finish(job, "PENDING", Math.max(BACKOFF[attempts - 1], result.retryAfter()), result.error());
    }
}
