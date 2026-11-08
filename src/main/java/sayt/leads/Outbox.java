package sayt.leads;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import sayt.common.Audit;
import sayt.common.Problem;
import sayt.identity.ShopPrincipal;

@Service
class Outbox {
    private record Job(long id, long leadId, int attempts, UUID token) {}
    private static final int[] BACKOFF = {60, 300, 900, 3600, 21600, 21600};
    private final JdbcClient db;
    private final Audit audit;
    private final Telegram telegram;

    Outbox(JdbcClient db, Audit audit, Telegram telegram) {
        this.db = db;
        this.audit = audit;
        this.telegram = telegram;
    }

    Object list(int page) {
        var jobs = db.sql("""
            select id,lead_id,status,attempts,next_attempt,last_error from notification_outbox
            order by (status='FAILED') desc,id desc limit 24 offset ?
            """).param(page * 24).query().listOfRows();
        return Map.of("enabled", telegram.enabled(), "items", jobs);
    }

    @Transactional
    void retry(long id, long actor) {
        if (!telegram.enabled()) throw Problem.conflict("NOTIFICATIONS_DISABLED");
        int updated = db.sql("""
            update notification_outbox set status='PENDING',attempts=0,next_attempt=now(),last_error=null
            where id=? and status='FAILED'
            """).param(id).update();
        if (updated != 1) throw Problem.conflict("NOTIFICATION_NOT_FAILED");
        audit.record(actor, "NOTIFICATION_RETRY", "notification:" + id);
    }

    @Scheduled(fixedDelayString = "PT3S", initialDelayString = "PT3S")
    void drain() {
        if (!telegram.enabled() || System.getProperty("shop.owner-command") != null) return;
        for (int count = 0; count < 8; count++) {
            var job = claim();
            if (job.isEmpty()) return;
            deliver(job.get());
        }
    }

    private Optional<Job> claim() {
        return db.sql("""
            with next as (
              select id from notification_outbox where status='PENDING' and next_attempt<=now()
                and (lease_until is null or lease_until<=now())
              order by next_attempt,id for update skip locked limit 1
            )
            update notification_outbox o set lease_until=now()+interval '45 seconds',lease_token=?
            from next where o.id=next.id returning o.id,o.lead_id,o.attempts,o.lease_token
            """).param(UUID.randomUUID()).query((rs, index) ->
                new Job(rs.getLong(1), rs.getLong(2), rs.getInt(3), rs.getObject(4, UUID.class))).optional();
    }

    private void deliver(Job job) {
        var message = db.sql("""
            select s.name shop,l.public_number,l.name,l.phone,l.product_name,l.sku,l.variant_label,l.price,l.created_at
            from lead l cross join shop_settings s where l.id=? and l.anonymized_at is null
            """).param(job.leadId()).query((rs, index) -> """
            %s
            Заявка / Ariza %s
            %s · %s
            %s · %s · %s
            %s UZS
            %s
            """.formatted(rs.getString("shop"), rs.getString("public_number"), rs.getString("name"),
                rs.getString("phone"), rs.getString("product_name"), rs.getString("variant_label"),
                rs.getString("sku"), rs.getBigDecimal("price").stripTrailingZeros().toPlainString(),
                rs.getObject("created_at", OffsetDateTime.class).atZoneSameInstant(ZoneId.of("Asia/Tashkent"))
                    .format(DateTimeFormatter.ofPattern("dd.MM.uuuu HH:mm VV")))).optional();
        if (message.isEmpty()) {
            finish(job, "CANCELLED", 0, null);
            return;
        }
        var result = telegram.send(message.get());
        int attempts = job.attempts() + 1;
        if (result.sent()) finish(job, "SENT", 0, null);
        else if (attempts > BACKOFF.length) finish(job, "FAILED", 0, result.error());
        else finish(job, "PENDING", Math.max(BACKOFF[attempts - 1], result.retryAfter()), result.error());
    }

    private void finish(Job job, String status, int delay, String error) {
        db.sql("""
            update notification_outbox set status=?,attempts=attempts+1,next_attempt=now()+make_interval(secs=>?),
                last_error=?,lease_until=null,lease_token=null where id=? and lease_token=?
            """).params(status, delay, error, job.id(), job.token()).update();
    }
}

@RestController
@RequestMapping("/api/v1/admin/notifications")
class NotificationsApi {
    private final Outbox outbox;
    NotificationsApi(Outbox outbox) { this.outbox = outbox; }

    @GetMapping
    Object list(@RequestParam(defaultValue="0") @jakarta.validation.constraints.Min(0)
        @jakarta.validation.constraints.Max(10000) int page) { return outbox.list(page); }

    @PostMapping("/{id}/retry")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void retry(@PathVariable long id, @AuthenticationPrincipal ShopPrincipal actor) {
        outbox.retry(id, actor.id());
    }
}
