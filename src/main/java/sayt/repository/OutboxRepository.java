package sayt.repository;

import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import sayt.dto.notification.LeadNotification;

@Repository
public class OutboxRepository {
    public record Job(long id, long leadId, int attempts, UUID token) {}

    private final JdbcClient db;

    OutboxRepository(JdbcClient db) { this.db = db; }

    public List<Map<String, Object>> list(int page) {
        return db.sql("""
            select id,lead_id,status,attempts,next_attempt,last_error from notification_outbox
            order by (status='FAILED') desc,id desc limit 24 offset ?
            """).param(page * 24).query().listOfRows();
    }

    public int retry(long id) {
        return db.sql("""
            update notification_outbox set status='PENDING',attempts=0,next_attempt=now(),last_error=null
            where id=? and status='FAILED'
            """).param(id).update();
    }

    public Optional<Job> claim() {
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

    public Optional<LeadNotification> message(long leadId) {
        return db.sql("""
            select s.name shop,l.public_number,l.name,l.phone,l.product_name,l.sku,l.variant_label,l.price,l.created_at
            from lead l cross join shop_settings s where l.id=? and l.anonymized_at is null
            """).param(leadId).query((rs, index) -> new LeadNotification(rs.getString("shop"),
                rs.getObject("public_number", UUID.class), rs.getString("name"), rs.getString("phone"),
                rs.getString("product_name"), rs.getString("sku"), rs.getString("variant_label"),
                rs.getBigDecimal("price"), rs.getObject("created_at", OffsetDateTime.class))).optional();
    }

    public void finish(Job job, String status, int delay, String error) {
        db.sql("""
            update notification_outbox set status=?,attempts=attempts+1,next_attempt=now()+make_interval(secs=>?),
                last_error=?,lease_until=null,lease_token=null where id=? and lease_token=?
            """).params(status, delay, error, job.id(), job.token()).update();
    }

    public void enqueue(long leadId) {
        db.sql("insert into notification_outbox(lead_id,status) values (?,'PENDING')").param(leadId).update();
    }

    public boolean leaseActive(long leadId) {
        return db.sql("select coalesce(lease_until > now(), false) from notification_outbox where lead_id=? for update")
            .param(leadId).query(Boolean.class).optional().orElse(false);
    }

    public void cancelForLead(long leadId) {
        db.sql("""
            update notification_outbox set status='CANCELLED',last_error=null,lease_until=null,lease_token=null
            where lead_id=? and status in ('PENDING','FAILED')
            """).param(leadId).update();
    }
}
