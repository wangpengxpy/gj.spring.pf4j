package ${packagePrefix}.${pluginName}.modelmapper;

import ${packagePrefix}.${pluginName}.dto.EgroupDTO;
import iotcenter.data.model.EGroup;
import iotcenter.pf4j.modelmapper.IModelMapperConfig;
import iotcenter.pf4j.modelmapper.IoTTypeMapConfig;

import org.pf4j.Extension;

import java.util.List;

@Extension
public class ${pluginName}ModelMapperConfig implements IModelMapperConfig {
    @Override
    public List<IoTTypeMapConfig> getTypeMapConfigs() {
        return List.of(
                IoTTypeMapConfig.of(EGroup.class, EgroupDTO.class, typeMap -> {
                    typeMap.addMapping(EGroup::getGatewayId, EgroupDTO::setGatewayId);
                    typeMap.addMapping(EGroup::getGroupName, EgroupDTO::setGroupName);
                    typeMap.addMapping(ctx -> null, EgroupDTO::setGroupId);
                })
        );
    }
}
