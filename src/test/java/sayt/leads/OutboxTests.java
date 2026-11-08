package sayt.leads;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import sayt.common.Audit;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:postgresql://127.0.0.1:55432/rms_test",
    "spring.datasource.username=rms_test",
    "spring.datasource.password=rms-test-placeholder",
    "spring.flyway.user=rms_test",
    "spring.flyway.password=rms-test-placeholder",
    "shop.privacy-version=test-v1"
})
@EnabledIfEnvironmentVariable(named = "RMS_TEST_ONLY", matches = "yes")
@Transactional
class OutboxTests {
    @Autowired JdbcClient db;
    @Autowired Leads leads;
    @Autowired Outbox outbox;
    @Autowired Audit audit;
    @MockitoBean Telegram telegram;

    @Test
    void keepsLeadAfterFailureAndResumesExpiredLeaseWithNewWorker() {
        String tag = UUID.randomUUID().toString();
        long product = db.sql("""
            insert into product(category_id,brand,name,slug,status)
            select id,'QA','Model',?,'PUBLISHED' from category where slug='phones' returning id
            """).param(tag).query(Long.class).single();
        long variant = db.sql("""
            insert into product_variant(product_id,sku,stock,price)
            values (?,?,'IN_STOCK',3200000) returning id
            """).params(product, tag).query(Long.class).single();
        when(telegram.enabled()).thenReturn(true);
        when(telegram.send(anyString())).thenReturn(new Telegram.Delivery(false, "TELEGRAM_HTTP_429", 120),
            new Telegram.Delivery(true, null, 0));

        var receipt = leads.create(new Leads.Input("Test Contact", "+998 90 123 45 68", variant,
            new BigDecimal("3200000"), "test-v1", true), UUID.randomUUID(), tag, "test");
        long job = db.sql("""
            select o.id from notification_outbox o join lead l on l.id=o.lead_id where l.public_number=?
            """).param(receipt.number()).query(Long.class).single();
        assertEquals("PENDING", status(job));

        outbox.drain();
        assertEquals("PENDING", status(job));
        assertTrue(db.sql("select next_attempt >= now()+interval '120 seconds' from notification_outbox where id=?")
            .param(job).query(Boolean.class).single());
        db.sql("""
            update notification_outbox set next_attempt=now()-interval '1 second',
              lease_until=now()+interval '30 seconds', lease_token=? where id=?
            """).params(UUID.randomUUID(), job).update();
        var restarted = new Outbox(db, audit, telegram);
        restarted.drain();
        verify(telegram, times(1)).send(anyString());

        db.sql("update notification_outbox set lease_until=now()-interval '1 second' where id=?").param(job).update();
        restarted.drain();
        assertEquals("SENT", status(job));
        verify(telegram, times(2)).send(contains(receipt.number().toString()));
        assertTrue(db.sql("select exists(select 1 from lead where public_number=?)")
            .param(receipt.number()).query(Boolean.class).single());
    }

    private String status(long id) {
        return db.sql("select status from notification_outbox where id=?").param(id).query(String.class).single();
    }
}
