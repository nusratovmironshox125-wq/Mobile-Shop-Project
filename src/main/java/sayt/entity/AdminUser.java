package sayt.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "admin_user")
public class AdminUser {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String login;
    private String name;
    private String passwordHash;
    private String role;
    private boolean enabled = true;
    private boolean mustChange;
    private long authVersion;
    private Instant createdAt = Instant.now();
    private Instant lastLogin;
    @Version private long version;
}
