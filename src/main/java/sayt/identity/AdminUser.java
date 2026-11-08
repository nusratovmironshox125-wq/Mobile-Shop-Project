package sayt.identity;

import jakarta.persistence.*;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

@Entity
@Table(name = "admin_user")
class AdminUser {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;
    String login;
    String name;
    String passwordHash;
    String role;
    boolean enabled = true;
    boolean mustChange;
    long authVersion;
    Instant createdAt = Instant.now();
    Instant lastLogin;
    @Version long version;
}

interface AdminUsers extends JpaRepository<AdminUser, Long> {
    Optional<AdminUser> findByLogin(String login);
}
