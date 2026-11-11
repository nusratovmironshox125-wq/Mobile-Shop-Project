package sayt.repository;

import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import sayt.dto.catalog.Selection;
import sayt.dto.lead.*;
import sayt.mapper.LeadMapper;

@Repository
public class LeadRepository {
    public record Previous(UUID number, String hash) {}

    private final JdbcClient db;

    LeadRepository(JdbcClient db) { this.db = db; }

    public Optional<Previous> lockAndFindReplay(UUID key) {
        db.sql("select 1 from pg_advisory_xact_lock(hashtextextended(cast(? as text), 0))")
            .param(key).query(Integer.class).single();
        return db.sql("select public_number,request_hash from lead where idempotency_key=?").param(key)
            .query((rs, index) -> new Previous(rs.getObject(1, UUID.class), rs.getString(2))).optional();
    }

    public long insert(UUID number, UUID key, String hash, String name, String phone, Selection selected, String privacyVersion) {
        return db.sql("""
            insert into lead (public_number,idempotency_key,request_hash,name,phone,product_id,variant_id,
                product_name,sku,variant_label,price,original_price,installment,installment_terms,status,privacy_version)
            values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,'NEW',?) returning id
            """).params(number, key, hash, name, phone, selected.productId(), selected.variantId(),
                selected.productName(), selected.sku(), selected.label(), selected.price(), selected.originalPrice(),
                selected.installment(), selected.installmentTerms(), privacyVersion).query(Long.class).single();
    }

    public LeadListResponse list(String status, String query, OffsetDateTime from, OffsetDateTime until, int page, OffsetDateTime cutoff, long actor) {
        String search = query == null ? "" : query.strip();
        var sql = """
            select l.*,r.read_at from lead l left join lead_read r on r.lead_id=l.id and r.admin_id=:actor where true %s %s %s %s order by created_at desc,id desc limit 10 offset :skip
            """.formatted(status == null ? "" : "and status=:status",
            search.isEmpty() ? "" : "and (cast(public_number as text) ilike :q escape '!' or sku ilike :q escape '!' or phone=:phone)",
            from == null ? "" : "and created_at >= :from", until == null ? "" : "and created_at < :until");
        var countSql = sql.substring(sql.indexOf(" from "), sql.indexOf(" order by "));
        var params = new java.util.HashMap<String,Object>();
        params.put("actor",actor);
        if(status != null) params.put("status",status);
        if(!search.isEmpty()) { params.put("q","%" + search.replace("!","!!").replace("%","!%").replace("_","!_") + "%"); params.put("phone",search.replaceAll("[ ()-]","")); }
        if(from != null) params.put("from",from);
        if(until != null) params.put("until",until);
        long total = db.sql("select count(*)"+countSql).params(params).query(Long.class).single();
        var stmt = db.sql(sql).param("skip", page * 10).param("actor",actor);
        if (status != null) stmt.param("status", status);
        if (!search.isEmpty()) {
            stmt.param("q", "%" + search.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%")
                .param("phone", search.replaceAll("[ ()-]", ""));
        }
        if (from != null) stmt.param("from", from);
        if (until != null) stmt.param("until", until);
        var items = stmt.query((row, index) -> LeadMapper.view(row, cutoff)).list();
        return new LeadListResponse(items, (page+1L)*10 < total, page, 10, total, (int)((total+9)/10));
    }

    public Optional<LeadResponse> find(long id, OffsetDateTime cutoff) {
        return db.sql("select *,null::timestamptz read_at from lead where id=?").param(id).query((row, index) -> LeadMapper.view(row, cutoff)).optional();
    }

    public Optional<LeadResponse> lock(long id, OffsetDateTime cutoff) {
        return db.sql("select *,null::timestamptz read_at from lead where id=? for update").param(id).query((row, index) -> LeadMapper.view(row, cutoff)).optional();
    }

    public List<Map<String, Object>> history(long id) {
        return db.sql("""
            select actor_id, previous_status, new_status, reason, created_at
            from lead_status_history where lead_id=? order by id desc
            """).param(id).query().listOfRows();
    }

    public int update(long id, long version, String status, String comment, OffsetDateTime closed) {
        return db.sql("""
            update lead set status=?, comment=?, closed_at=?, version=version+1 where id=? and version=?
            """).params(status, comment, closed, id, version).update();
    }

    public void addHistory(long id, long actor, String previous, String status, String reason) {
        db.sql("insert into lead_status_history(lead_id,actor_id,previous_status,new_status,reason) values (?,?,?,?,?)")
            .params(id, actor, previous, status, reason).update();
    }

    public List<Long> expiredIds(OffsetDateTime cutoff) {
        return db.sql("""
            select id from lead where closed_at < ? and anonymized_at is null
            order by closed_at limit 100
            """).param(cutoff).query(Long.class).list();
    }

    public boolean anonymize(long id) {
        int changed = db.sql("""
            update lead set name=null,phone=null,comment=null,anonymized_at=now(),version=version+1
            where id=? and anonymized_at is null
            """).param(id).update();
        if (changed > 0) db.sql("update lead_status_history set reason=null where lead_id=?").param(id).update();
        return changed > 0;
    }
}
