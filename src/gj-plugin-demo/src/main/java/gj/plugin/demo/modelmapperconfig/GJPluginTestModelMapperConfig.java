package gj.plugin.demo.modelmapperconfig;

import gj.pf4j.modelmapper.GJPluginModelMapperConfig;
import gj.pf4j.modelmapper.GJPluginTypeMapConfig;
import gj.plugin.demo.examples.Order;
import gj.plugin.demo.examples.OrderDTO;
import org.modelmapper.TypeMap;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class GJPluginTestModelMapperConfig implements GJPluginModelMapperConfig {

    @Override
    public List<GJPluginTypeMapConfig> getTypeMapConfigs() {
        return List.of(GJPluginTypeMapConfig.of(Order.class, OrderDTO.class, (TypeMap<Order, OrderDTO> typeMap) -> {
            typeMap.addMappings(mapper ->
                    mapper.skip(OrderDTO::setCustomerFirstName));
        }));
    }
}
