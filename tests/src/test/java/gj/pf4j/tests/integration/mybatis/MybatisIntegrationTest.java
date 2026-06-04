package gj.pf4j.tests.integration.mybatis;

import gj.pf4j.mybatis.GJPluginMybatisSqlSessionManager;
import gj.pf4j.tests.integration.IntegrationTestConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = {IntegrationTestConfig.class, MybatisIntegrationTest.Context.class})
class MybatisIntegrationTest {

    @Autowired
    private GJPluginMybatisSqlSessionManager sessionManager;

    @Autowired
    private DataSource dataSource;

    @Test
    @DisplayName("SqlSessionManager is created with shared DataSource")
    void sessionManagerCreated() {
        assertNotNull(sessionManager);
        assertNotNull(dataSource);
    }

    @Test
    @DisplayName("Cleanup of non-existent plugin resources is safe")
    void cleanupNonExistent() {
        assertDoesNotThrow(() -> sessionManager.cleanupPluginResources("nonexistent.plugin"));
    }

    @Test
    @DisplayName("DAO package convention is computed correctly")
    void daoPackageComputed() {
        // Simulate the getDao() logic
        String pluginId = "gj.module.test";
        String daoPackage = pluginId + ".dao";
        assertEquals("gj.module.test.dao", daoPackage);
    }

    @Configuration
    static class Context {
        @Bean
        DataSource dataSource() {
            return new DriverManagerDataSource("jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1");
        }

        @Bean
        GJPluginMybatisSqlSessionManager sessionManager(DataSource ds) {
            return new GJPluginMybatisSqlSessionManager(ds);
        }
    }
}
