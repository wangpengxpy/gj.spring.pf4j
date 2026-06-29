package gj.plugin.demo.mvc.modelmapper;

import gj.pf4j.modelmapper.GJPluginModelMapperConfig;
import gj.pf4j.modelmapper.GJPluginTypeMapConfig;
import gj.plugin.demo.mvc.dto.MvcUserCreateRequest;
import gj.plugin.demo.mvc.dto.MvcUserResponse;
import gj.plugin.demo.mvc.model.MvcUser;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class MvcModelMapperConfig implements GJPluginModelMapperConfig {

    @Override
    public List<GJPluginTypeMapConfig> getTypeMapConfigs() {
        return List.of(
                GJPluginTypeMapConfig.of(MvcUser.class, MvcUserResponse.class),
                GJPluginTypeMapConfig.of(MvcUserCreateRequest.class, MvcUser.class)
        );
    }
}
