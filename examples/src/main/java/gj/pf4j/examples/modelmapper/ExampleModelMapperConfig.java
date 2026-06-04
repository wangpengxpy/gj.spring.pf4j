package gj.pf4j.examples.modelmapper;

import gj.pf4j.examples.dto.ExampleDTO;
import gj.pf4j.examples.mybatis.ExampleEntity;
import gj.pf4j.modelmapper.GJPluginModelMapperConfig;
import gj.pf4j.modelmapper.GJPluginTypeMapConfig;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ExampleModelMapperConfig implements GJPluginModelMapperConfig {

    @Override
    public List<GJPluginTypeMapConfig> getTypeMapConfigs() {
        return List.of(
            GJPluginTypeMapConfig.of(ExampleEntity.class, ExampleDTO.class)
        );
    }
}
