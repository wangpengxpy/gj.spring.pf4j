package gj.plugin.demo.webflux.modelmapper;

import gj.pf4j.modelmapper.GJPluginModelMapperConfig;
import gj.pf4j.modelmapper.GJPluginTypeMapConfig;
import gj.plugin.demo.entity.WebFluxUserEntity;
import gj.plugin.demo.webflux.dto.WebFluxUserCreateRequest;
import gj.plugin.demo.webflux.dto.WebFluxUserResponse;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class WebFluxModelMapperConfig implements GJPluginModelMapperConfig {

    @Override
    public List<GJPluginTypeMapConfig> getTypeMapConfigs() {
        return List.of(
                GJPluginTypeMapConfig.of(WebFluxUserEntity.class, WebFluxUserResponse.class),
                GJPluginTypeMapConfig.of(WebFluxUserCreateRequest.class, WebFluxUserEntity.class)
        );
    }
}
