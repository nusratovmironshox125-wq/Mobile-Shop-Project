package sayt.common;

import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
public class Audit {
    private final JdbcClient db;

    public Audit(JdbcClient db) {
        this.db = db;
    }

    public void record(Long actor, String action, String target) {
        var request = RequestContextHolder.getRequestAttributes();
        UUID correlation = request instanceof ServletRequestAttributes attrs
            ? Errors.correlation(attrs.getRequest()) : UUID.randomUUID();
        db.sql("insert into audit_event(actor_id, action, target, correlation_id) values (?,?,?,?)")
            .param(actor).param(action).param(target).param(correlation).update();
    }
}
