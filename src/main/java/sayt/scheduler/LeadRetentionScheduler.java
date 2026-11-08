package sayt.scheduler;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import sayt.exception.Problem;
import sayt.service.LeadPrivacyService;

@Component
class LeadRetentionScheduler {
    private final LeadPrivacyService privacy;

    LeadRetentionScheduler(LeadPrivacyService privacy) { this.privacy = privacy; }

    @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT1M")
    void run() {
        var cutoff = privacy.cutoff();
        for (long id : privacy.expiredIds(cutoff)) {
            try {
                privacy.anonymizeExpired(id, cutoff);
            } catch (Problem error) {
                if (!"NOTIFICATION_IN_PROGRESS".equals(error.code)) throw error;
            }
        }
    }
}
