package sayt.repository;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import sayt.dto.lead.LeadUpdates;

@Repository
public class LeadReadRepository {
    private final JdbcClient db;
    public LeadReadRepository(JdbcClient db) { this.db = db; }

    public LeadUpdates updates(long admin) {
        var counts = db.sql("""
            select count(*) filter(where r.lead_id is null) unread,coalesce(max(l.id),0) latest
            from lead l left join lead_read r on r.lead_id=l.id and r.admin_id=?
            """).param(admin).query((rs,n) -> new long[]{rs.getLong(1), rs.getLong(2)}).single();
        var recent = db.sql("""
            select l.id,l.public_number,l.product_name from lead l
            left join lead_read r on r.lead_id=l.id and r.admin_id=?
            where r.lead_id is null order by l.id desc limit 10
            """).param(admin).query((rs,n) -> new LeadUpdates.Item(rs.getLong(1),
                rs.getObject(2, java.util.UUID.class), rs.getString(3))).list();
        return new LeadUpdates(counts[0], counts[1], recent);
    }

    public boolean mark(long admin, long lead) {
        return db.sql("""
            insert into lead_read(admin_id,lead_id) select ?,id from lead where id=?
            on conflict do nothing
            """).params(admin,lead).update() > 0;
    }
}
