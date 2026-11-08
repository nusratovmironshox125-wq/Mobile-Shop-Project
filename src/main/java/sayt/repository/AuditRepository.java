package sayt.repository;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class AuditRepository {
    private final JdbcClient db;

    AuditRepository(JdbcClient db) { this.db = db; }

    public void insert(Long actor, String action, String target, UUID correlation) {
        db.sql("insert into audit_event(actor_id, action, target, correlation_id) values (?,?,?,?)")
            .param(actor).param(action).param(target).param(correlation).update();
    }

    public List<Map<String, Object>> list(int page) {
        return db.sql("""
            select id, actor_id, action, target, correlation_id, created_at
            from audit_event order by created_at desc, id desc limit 24 offset ?
            """).param(page * 24).query().listOfRows();
    }
}
