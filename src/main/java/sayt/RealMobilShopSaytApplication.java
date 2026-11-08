package sayt;

import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class RealMobilShopSaytApplication {
    public static void main(String[] args) {
        var app = new SpringApplication(RealMobilShopSaytApplication.class);
        if (args.length == 1 && "inspect-database".equals(args[0])) {
            app.setWebApplicationType(WebApplicationType.NONE);
            app.run("--spring.profiles.active=local,db-inspect", "--spring.flyway.enabled=false",
                "--spring.sql.init.mode=never", "--spring.jpa.hibernate.ddl-auto=none",
                "--spring.jpa.properties.hibernate.hbm2ddl.auto=none",
                "--spring.jpa.properties.jakarta.persistence.schema-generation.database.action=none",
                "--spring.datasource.hikari.read-only=true", "--spring.jpa.show-sql=false",
                "--logging.level.org.hibernate.orm.connections.pooling=WARN").close();
        } else if (args.length == 1 && ("bootstrap-owner".equals(args[0]) || "recover-owner".equals(args[0]))) {
            app.setWebApplicationType(WebApplicationType.NONE);
            System.setProperty("shop.owner-command", args[0].equals("recover-owner") ? "recover" : "bootstrap");
            app.setDefaultProperties(Map.of("spring.main.banner-mode", "off"));
            app.run().close();
        } else {
            app.run(args);
        }
    }

}
