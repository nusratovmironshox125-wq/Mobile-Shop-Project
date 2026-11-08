package sayt.service;

import sayt.dto.notification.LeadNotification;

public interface NotificationChannel {
    /** A failed send reports a safe code and may request a minimum retry delay in seconds. */
    record Delivery(boolean sent, String error, int retryAfter) {}

    boolean enabled();
    Delivery send(LeadNotification message);
}
