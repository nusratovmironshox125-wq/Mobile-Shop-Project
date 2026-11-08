package sayt;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:postgresql://127.0.0.1:55432/rms_test",
    "spring.datasource.username=rms_test",
    "spring.datasource.password=rms-test-placeholder",
    "spring.flyway.user=rms_test",
    "spring.flyway.password=rms-test-placeholder",
    "shop.telegram.token=",
    "shop.telegram.chat="
})
@EnabledIfEnvironmentVariable(named = "RMS_TEST_ONLY", matches = "yes")
class RealMobilShopSaytApplicationTests {
    @Test
    void contextLoadsWithPostgreSql() {}
}
