package sayt.config;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.*;
import sayt.service.DatabaseInspectionService;

@Configuration
@Profile("db-inspect")
class DatabaseInspectionConfig {
    @Bean
    ApplicationRunner inspectDatabase(DatabaseInspectionService service) {
        return args -> {
            var actual = service.inspect();
            System.out.printf("JPA DataSource: database=%s schema=%s user=%s host=%s port=%d readOnly=%s%n",
                actual.database(), actual.schema(), actual.user(), actual.address(), actual.port(), actual.readOnly());
            System.out.printf("PostgreSQL %s, encoding=%s, search_path=%s%n",
                actual.version(), actual.encoding(), actual.searchPath());
            System.out.printf("Tables in the active schema (%d): %s%n", actual.tables().size(), String.join(", ", actual.tables()));
            if (!DatabaseInspectionService.matchesOwnerDatabase(actual)) {
                throw new IllegalStateException("AT-26: DataSource does not match the owner local PostgreSQL");
            }
            System.out.println("AT-26 DataSource: MATCH. The write and IntelliJ verification are separate steps.");
        };
    }
}
