package sayt.service;

import java.util.UUID;
import java.util.List;
import java.util.Map;
import sayt.exception.GlobalExceptionHandler;
import sayt.repository.AuditRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
public class AuditService {
    private final AuditRepository repository;
    private final tools.jackson.databind.ObjectMapper json;

    public AuditService(AuditRepository repository, tools.jackson.databind.ObjectMapper json) {
        this.repository = repository;
        this.json=json;
    }

    public void record(Long actor, String action, String target) {
        var request = RequestContextHolder.getRequestAttributes();
        UUID correlation = request instanceof ServletRequestAttributes attrs
            ? GlobalExceptionHandler.correlation(attrs.getRequest()) : UUID.randomUUID();
        repository.insert(actor, action, target, correlation, null, "{}");
    }

    private static final java.util.Set<String> ALLOWED = java.util.Set.of(
        "categoryId","brand","name","slug","description","status","sku","color","ramGb","storageGb",
        "screen","cpu","gpu","cameras","battery","sim","os","weight","notes","price","stock","installment",
        "installmentTerms","salePrice","saleFrom","saleUntil","storageType","specsReviewRequired",
        "nameRu","nameUz","kind","enabled","sortOrder","login","role","hours","address","directions","mapUrl");

    public void change(Long actor, String action, String target, String label, Object before, Object after) {
        var old = fields(before);
        var now = fields(after);
        var diff = new java.util.LinkedHashMap<String,Object>();
        now.forEach((key,value) -> {
            if (!java.util.Objects.equals(old.get(key),value)) {
                var pair=new java.util.LinkedHashMap<String,Object>();
                pair.put("before",old.get(key)); pair.put("after",value); diff.put(key,pair);
            }
        });
        write(actor,action,target,label,diff);
    }
    public void status(Long actor,String action,String target,String label,String before,String after) {
        write(actor,action,target,label,java.util.Objects.equals(before,after) ? Map.of() :
            Map.of("status",Map.of("before",before,"after",after)));
    }
    private Map<String,Object> fields(Object record) {
        var result=new java.util.LinkedHashMap<String,Object>();
        if(record==null) return result;
        if(!record.getClass().isRecord()) throw new IllegalArgumentException("Audit requires a safe record DTO");
        for(var field:record.getClass().getRecordComponents()) if(ALLOWED.contains(field.getName()) || record instanceof sayt.dto.settings.ShopSettingsResponse && java.util.Set.of("phone","telegram").contains(field.getName())) {
            try { result.put(field.getName(),field.getAccessor().invoke(record)); }
            catch(ReflectiveOperationException error) { throw new IllegalStateException(error); }
        }
        return result;
    }
    private void write(Long actor,String action,String target,String label,Map<String,Object> changes) {
        var request=RequestContextHolder.getRequestAttributes();
        UUID correlation=request instanceof ServletRequestAttributes attrs ?
            GlobalExceptionHandler.correlation(attrs.getRequest()) : UUID.randomUUID();
        repository.insert(actor,action,target,correlation,label,json.writeValueAsString(changes));
    }
    @org.springframework.transaction.annotation.Transactional(readOnly=true,
        isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public sayt.dto.PageResult<Map<String,Object>> list(int page,String username,String action,String object,
            java.time.LocalDate from,java.time.LocalDate until) {
        if(from!=null && until!=null && from.isAfter(until)) throw sayt.exception.Problem.bad("DATE_RANGE");
        var zone=java.time.ZoneId.of("Asia/Tashkent");
        return repository.list(page,username,action,object,
            from==null?null:from.atStartOfDay(zone).toOffsetDateTime(),
            until==null?null:until.plusDays(1).atStartOfDay(zone).toOffsetDateTime());
    }
}
