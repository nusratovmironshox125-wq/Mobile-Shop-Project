package sayt.scheduler;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import sayt.service.NotificationService;

@Component
class NotificationScheduler {
    private final NotificationService notifications;

    NotificationScheduler(NotificationService notifications) { this.notifications = notifications; }

    @Scheduled(fixedDelayString = "PT3S", initialDelayString = "PT3S")
    void run() { notifications.drain(); }
}
