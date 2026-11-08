package sayt.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import sayt.entity.ShopSettings;

public interface SettingsRepository extends JpaRepository<ShopSettings, Long> {}
