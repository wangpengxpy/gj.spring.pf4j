package ${packagePrefix}.${pluginName}.modelmapper;

import ${packagePrefix}.${pluginName}.dto.EgroupDTO;
import iotcenter.data.model.EGroup;
import gj.pf4j.modelmapper.GJPluginModelMapperConfig;
import gj.pf4j.modelmapper.GJPluginTypeMapConfig;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ${pluginName}ModelMapperConfig implements GJPluginModelMapperConfig {
    @Override
    public List<GJPluginTypeMapConfig> getTypeMapConfigs() {
        return List.of(
                GJPluginTypeMapConfig.of(EGroup.class, EgroupDTO.class, typeMap -> {
                    typeMap.addMapping(EGroup::getGatewayId, EgroupDTO::setGatewayId);
                    typeMap.addMapping(EGroup::getGroupName, EgroupDTO::setGroupName);
                    typeMap.addMapping(ctx -> null, EgroupDTO::setGroupId);
                })
        );
    }
}
