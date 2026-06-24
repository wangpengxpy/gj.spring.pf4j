package gj.pf4j.descriptor;

import org.pf4j.DefaultPluginDescriptor;
import org.pf4j.PluginDescriptor;
import org.pf4j.PropertiesPluginDescriptorFinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;

public class GJPropertiesPluginDescriptorFinder extends PropertiesPluginDescriptorFinder {

    private static final Logger log = LoggerFactory.getLogger(GJPropertiesPluginDescriptorFinder.class);

    static final String PLUGIN_ORDER = "plugin.order";

    @Override
    protected DefaultPluginDescriptor createPluginDescriptorInstance() {
        return new GJPluginDescriptor();
    }

    @Override
    protected PluginDescriptor createPluginDescriptor(Properties properties) {
        GJPluginDescriptor descriptor = (GJPluginDescriptor) super.createPluginDescriptor(properties);
        String orderValue = properties.getProperty(PLUGIN_ORDER);
        if (orderValue != null && !orderValue.isBlank()) {
            try {
                descriptor.setOrder(Integer.parseInt(orderValue.trim()));
            } catch (NumberFormatException e) {
                log.warn("Invalid plugin.order value '{}', using default {}", orderValue, descriptor.getOrder());
            }
        }
        return descriptor;
    }
}
