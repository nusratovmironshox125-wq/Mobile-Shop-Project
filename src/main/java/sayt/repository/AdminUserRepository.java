package sayt.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import sayt.entity.AdminUser;

public interface AdminUserRepository extends JpaRepository<AdminUser, Long> {
    Optional<AdminUser> findByLogin(String login);
    boolean existsByIdAndEnabledTrueAndAuthVersion(long id, long authVersion);
    boolean existsByRole(String role);

    @Modifying
    @Query(value = "update admin_user set last_login=now() where id=:id", nativeQuery = true)
    void markLogin(long id);

    @Query(value = "select 1 from pg_advisory_xact_lock(78198312)", nativeQuery = true)
    int lockOwnerBootstrap();
}
