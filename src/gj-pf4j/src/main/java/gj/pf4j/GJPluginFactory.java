package gj.pf4j;

import gj.pf4j.descriptor.GJPluginDescriptor;
import gj.pf4j.lifecycle.PluginResourceRegistrar;
import lombok.extern.slf4j.Slf4j;
import org.pf4j.Plugin;
import org.pf4j.PluginFactory;
import org.pf4j.PluginWrapper;
import org.springframework.context.support.GenericApplicationContext;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.List;

@Slf4j
public class GJPluginFactory implements PluginFactory {
    @Override
    public Plugin create(PluginWrapper wrapper) {
        try {
            String pluginClassName = wrapper.getDescriptor().getPluginClass();
            log.debug("Create instance for plugin '{}'", pluginClassName);
            Class<?> pluginClass;
            try {
                pluginClass = wrapper.getPluginClassLoader().loadClass(pluginClassName);
            } catch (ClassNotFoundException e) {
                throw new GJPluginException("Class " + pluginClassName + " not found, plugin or additional paths");
            }
            int modifiers = pluginClass.getModifiers();
            if (Modifier.isAbstract(modifiers) || Modifier.isInterface(modifiers)
                    || (!GJPlugin.class.isAssignableFrom(pluginClass))) {
                throw new GJPluginException("The plugin class " + pluginClassName + " is not valid");
            }
            GJPlugin plugin = createInstance(pluginClass, wrapper);
            var pluginManager = (GJPluginManager) wrapper.getPluginManager();
            var pluginContext = GJPluginContext.builder()
                    .pluginId(wrapper.getPluginId())
                    .classLoader(wrapper.getPluginClassLoader())
                    .descriptor((GJPluginDescriptor) wrapper.getDescriptor())
                    .everStarted(pluginManager.wasEverStarted(wrapper.getPluginId()))
                    .build();
            List<PluginResourceRegistrar> programmatic = pluginManager.getExternalRegistrars();
            return new GJSpringPlugin(pluginContext, plugin,
                    (GenericApplicationContext) pluginManager.getMainApplicationContext(),
                    programmatic);
        } catch (Exception e) {
            throw new RuntimeException("Failed to instantiate plugin：" + e.getMessage(), e);
        }
    }

    private GJPlugin createInstance(Class<?> pluginClass, PluginWrapper pluginWrapper) {
        try {
            Constructor<?> constructor = pluginClass.getConstructor(PluginWrapper.class);
            return (GJPlugin) constructor.newInstance(pluginWrapper);
        } catch (NoSuchMethodException e) {
            return createUsingNoParametersConstructor(pluginClass);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new GJPluginException(
                    "Failed to instantiate plugin class [" + pluginClass.getName() + "] with PluginWrapper parameter constructor",
                    e
            );
        }
    }

    private GJPlugin createUsingNoParametersConstructor(Class<?> pluginClass) {
        try {
            Constructor<?> constructor = pluginClass.getConstructor();
            return (GJPlugin) constructor.newInstance();
        } catch (NoSuchMethodException e) {
            log.error(e.getMessage(), e);
            throw new GJPluginException(
                    "Plugin class [" + pluginClass.getName() + "] has no valid constructor. Require a constructor with PluginWrapper parameter or a no-arg constructor",
                    e
            );
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new GJPluginException(
                    "Failed to instantiate plugin class [" + pluginClass.getName() + "] with no-arg constructor",
                    e
            );
        }
    }
}
