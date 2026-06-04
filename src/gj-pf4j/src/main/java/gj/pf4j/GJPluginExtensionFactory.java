/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j;

import org.pf4j.ExtensionFactory;
import org.pf4j.PluginWrapper;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.GenericApplicationContext;

public class GJPluginExtensionFactory implements ExtensionFactory {

    private final GJPluginManager pluginManager;

    public GJPluginExtensionFactory(GJPluginManager pluginManager) {
        this.pluginManager = pluginManager;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T create(Class<T> extensionClass) {
        GenericApplicationContext pluginApplicationContext = getApplicationContext(extensionClass);
        Object extension = null;
        try {
            extension = pluginApplicationContext.getBean(extensionClass);
        } catch (NoSuchBeanDefinitionException ignored) {
        } // do nothing
        if (extension == null) {
            Object extensionBean = createWithoutSpring(extensionClass);
            pluginApplicationContext.getBeanFactory().registerSingleton(
                    extensionClass.getName(), extensionBean);
            extension = extensionBean;
        }
        //no inspection unchecked
        return (T) extension;
    }

    public String getExtensionBeanName(Class<?> extensionClass) {
        ApplicationContext pluginAppCtx = getApplicationContext(extensionClass);
        if (pluginAppCtx == null) return null;
        String[] beanNames = pluginAppCtx.getBeanNamesForType(extensionClass);
        return beanNames.length > 0 ? beanNames[0] : null;
    }

    private Object createWithoutSpring(Class<?> extensionClass) {
        try {
            return extensionClass.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    private GenericApplicationContext getApplicationContext(Class<?> extensionClass) {
        PluginWrapper pluginWrapper = pluginManager.whichPlugin(extensionClass);
        GJSpringPlugin plugin = (GJSpringPlugin) pluginWrapper.getPlugin();
        return (GenericApplicationContext) plugin.getApplicationContext();
    }
}
