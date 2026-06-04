package gj.pf4j.tests.integration.modelmapper;

import gj.pf4j.modelmapper.*;
import gj.pf4j.tests.integration.IntegrationTestConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.stereotype.Component;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = IntegrationTestConfig.class)
class ModelMapperIntegrationTest {

    @Autowired
    private GJPluginModelMapperRegistry registry;

    @Autowired
    private ModelMapper modelMapper;

    @Test
    @DisplayName("Plugin ModelMapperConfig is discovered and mappings added to shared ModelMapper")
    void pluginConfigDiscoveredAndApplied() {
        AnnotationConfigApplicationContext fakePluginCtx = new AnnotationConfigApplicationContext();
        fakePluginCtx.registerBean(TestModelMapperConfig.class);
        fakePluginCtx.refresh();

        registry.registerModelMappers("test.plugin", fakePluginCtx);

        // Verify mapping works
        TestEntity entity = new TestEntity();
        entity.setId(1L);
        entity.setName("test");

        TestDTO dto = modelMapper.map(entity, TestDTO.class);
        assertNotNull(dto);
        assertEquals(1L, dto.getId());
        assertEquals("test", dto.getName());

        fakePluginCtx.close();
    }

    @Test
    @DisplayName("Empty config list does not throw")
    void emptyConfigDoesNotThrow() {
        AnnotationConfigApplicationContext fakePluginCtx = new AnnotationConfigApplicationContext();
        fakePluginCtx.refresh();

        assertDoesNotThrow(() -> registry.registerModelMappers("empty.plugin", fakePluginCtx));

        fakePluginCtx.close();
    }

    // Test POJOs
    public static class TestEntity {
        private Long id;
        private String name;
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }

    public static class TestDTO {
        private Long id;
        private String name;
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }

    @Component
    static class TestModelMapperConfig implements GJPluginModelMapperConfig {
        @Override
        public List<GJPluginTypeMapConfig> getTypeMapConfigs() {
            return List.of(GJPluginTypeMapConfig.of(TestEntity.class, TestDTO.class));
        }
    }
}
