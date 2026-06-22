package gj.pf4j.jpa;

import java.util.HashMap;
import java.util.Map;

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

    public String getDdlAuto() { return ddlAuto; }
    public void setDdlAuto(String ddlAuto) { this.ddlAuto = ddlAuto; }
    public boolean isShowSql() { return showSql; }
    public void setShowSql(boolean showSql) { this.showSql = showSql; }
    public boolean isFormatSql() { return formatSql; }
    public void setFormatSql(boolean formatSql) { this.formatSql = formatSql; }
    public String getDatabasePlatform() { return databasePlatform; }
    public void setDatabasePlatform(String databasePlatform) { this.databasePlatform = databasePlatform; }
    public boolean isGenerateStatistics() { return generateStatistics; }
    public void setGenerateStatistics(boolean generateStatistics) { this.generateStatistics = generateStatistics; }
    public Map<String, Object> getExtraProperties() { return extraProperties; }
    public void setExtraProperties(Map<String, Object> extraProperties) {
        this.extraProperties.clear();
        if (extraProperties != null) {
            this.extraProperties.putAll(extraProperties);
        }
    }
}
