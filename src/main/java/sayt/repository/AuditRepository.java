package sayt.repository;

import java.util.*;
import java.time.OffsetDateTime;
import sayt.dto.PageResult;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class AuditRepository {
    private final JdbcClient db;

    AuditRepository(JdbcClient db) { this.db = db; }

    public void insert(Long actor, String action, String target, UUID correlation, String label, String changes) {
        db.sql("""
            insert into audit_event(actor_id,actor_username,actor_role,action,target,correlation_id,object_label,changes,outcome)
            values (:actor,(select login from admin_user where id=:actor),
              (select role from admin_user where id=:actor),:action,:target,:correlation,:label,cast(:changes as jsonb),:outcome)
            """).param("actor",actor).param("action",action).param("target",target).param("correlation",correlation)
            .param("label",label).param("changes",changes).param("outcome",action.equals("LOGIN_FAILED") ? "DENIED" : "SUCCESS").update();
        if (actor == null && !action.equals("LOGIN_FAILED")) db.sql("""
            update audit_event set actor_username='SYSTEM' where correlation_id=? and action=? and actor_id is null
            """).params(correlation,action).update();
    }

    public PageResult<Map<String,Object>> list(int page, String username, String action, String object,
                                              OffsetDateTime from, OffsetDateTime until) {
        String where=" where true";
        var args=new HashMap<String,Object>();
        if(username!=null && !username.isBlank()) { where+=" and actor_username=:username"; args.put("username",username.strip()); }
        if(action!=null && !action.isBlank()) { where+=" and action=:action"; args.put("action",action); }
        if(object!=null && !object.isBlank()) { where+=" and (strpos(lower(coalesce(object_label,'')),lower(:object))>0 or target=:object)"; args.put("object",object.strip()); }
        if(from!=null) { where+=" and created_at>=:from"; args.put("from",from); }
        if(until!=null) { where+=" and created_at<:until"; args.put("until",until); }
        long total=db.sql("select count(*) from audit_event"+where).params(args).query(Long.class).single();
        var rows=db.sql("""
            select id,actor_id,actor_username,actor_role,action,target,object_label,changes::text changes,
                outcome,correlation_id,created_at from audit_event
            """+where+" order by created_at desc,id desc limit 10 offset :skip")
            .params(args).param("skip",page*10).query().listOfRows();
        return PageResult.of(rows,page,total);
    }
}
