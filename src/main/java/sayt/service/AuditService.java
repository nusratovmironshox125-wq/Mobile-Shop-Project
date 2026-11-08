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

    public AuditService(AuditRepository repository) {
        this.repository = repository;
    }

    public void record(Long actor, String action, String target) {
        var request = RequestContextHolder.getRequestAttributes();
        UUID correlation = request instanceof ServletRequestAttributes attrs
            ? GlobalExceptionHandler.correlation(attrs.getRequest()) : UUID.randomUUID();
        repository.insert(actor, action, target, correlation);
    }

    public List<Map<String, Object>> list(int page) { return repository.list(page); }
}
