package sayt.service;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sayt.repository.DatabaseInspectionRepository;
import sayt.repository.DatabaseInspectionRepository.ConnectionInfo;

@Service
@Profile("db-inspect")
public class DatabaseInspectionService {
    private final DatabaseInspectionRepository repository;

    DatabaseInspectionService(DatabaseInspectionRepository repository) { this.repository = repository; }

    @Transactional(readOnly = true)
    public ConnectionInfo inspect() { return repository.inspect(); }

    public static boolean matchesOwnerDatabase(ConnectionInfo actual) {
        return "real_mobil_shop_sayt".equals(actual.database()) && "public".equals(actual.schema())
            && "postgres".equals(actual.user()) && actual.port() == 5432 && actual.readOnly()
            && ("127.0.0.1".equals(actual.address()) || "::1".equals(actual.address()));
    }
}
