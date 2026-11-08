package sayt.repository;

import jakarta.persistence.EntityManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.Session;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("db-inspect")
public class DatabaseInspectionRepository {
    public record ConnectionInfo(String database, String schema, String user, String address, int port,
                                 boolean readOnly, String version, String encoding, String searchPath,
                                 List<String> tables) {}

    private final EntityManager entityManager;

    DatabaseInspectionRepository(EntityManager entityManager) { this.entityManager = entityManager; }

    public ConnectionInfo inspect() {
        return entityManager.unwrap(Session.class).doReturningWork(connection -> {
            try (var statement = connection.createStatement()) {
                statement.execute("SET TRANSACTION READ ONLY");
                String searchPath;
                try (var result = statement.executeQuery("SHOW search_path")) {
                    if (!result.next()) throw new SQLException("Missing search_path");
                    searchPath = result.getString(1);
                }
                var tables = new ArrayList<String>();
                try (var result = statement.executeQuery("""
                    select table_name from information_schema.tables
                    where table_schema=current_schema() and table_type='BASE TABLE' order by table_name
                    """)) {
                    while (result.next()) tables.add(result.getString(1));
                }
                try (var result = statement.executeQuery("""
                    select current_database(), current_schema(), current_user, host(inet_server_addr()),
                        inet_server_port(), current_setting('transaction_read_only'), current_setting('server_version'),
                        current_setting('server_encoding')
                    """)) {
                    if (!result.next()) throw new SQLException("Missing PostgreSQL connection metadata");
                    return new ConnectionInfo(result.getString(1), result.getString(2), result.getString(3),
                        result.getString(4), result.getInt(5), "on".equals(result.getString(6)), result.getString(7),
                        result.getString(8), searchPath, List.copyOf(tables));
                }
            }
        });
    }
}
