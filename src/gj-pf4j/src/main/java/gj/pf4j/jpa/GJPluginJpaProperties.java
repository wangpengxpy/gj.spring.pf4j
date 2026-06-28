package gj.pf4j.jpa;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
@ConfigurationProperties("gj.jpa")
public class GJPluginJpaProperties {

    private String ddlAuto = "none";

    private boolean showSql = false;

    private boolean formatSql = true;

    private String databasePlatform;

    private boolean generateStatistics = false;

    private final Map<String, Object> extraProperties = new HashMap<>();

    public Map<String, Object> toJpaPropertyMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("hibernate.hbm2ddl.auto", ddlAuto);
        map.put("hibernate.show_sql", showSql);
        map.put("hibernate.format_sql", formatSql);
        map.put("hibernate.generate_statistics", generateStatistics);
        map.put("hibernate.physical_naming_strategy",
                "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy");
        if (databasePlatform != null && !databasePlatform.isBlank()) {
            map.put("hibernate.dialect", databasePlatform);
        }
        map.putAll(extraProperties);
        return map;
    }
}
