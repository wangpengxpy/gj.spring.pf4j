/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.modelmapper;

import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.modelmapper.TypeMap;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
public class GJPluginModelMapperRegistry {

    public GJPluginModelMapperRegistry() {
        log.info("ModelMapperRegistry initialized");
    }

    public void registerModelMappers(String pluginId, ApplicationContext pluginCtx) {
        long startTime = System.currentTimeMillis();
        log.info("Starting to register model mappings for plugin: {}", pluginId);

        try {
            Map<String, GJPluginModelMapperConfig> beans =
                    pluginCtx.getBeansOfType(GJPluginModelMapperConfig.class);
            if (beans.isEmpty()) {
                log.debug("[Plugin: {}] No GJPluginModelMapperConfig beans found, skipping", pluginId);
                return;
            }

            List<GJPluginTypeMapConfig> allConfigs = new ArrayList<>();
            for (Map.Entry<String, GJPluginModelMapperConfig> entry : beans.entrySet()) {
                List<GJPluginTypeMapConfig> typeMapConfigs = entry.getValue().getTypeMapConfigs();
                allConfigs.addAll(typeMapConfigs);
                log.info("Loaded {} model mapping(s) from config: {}",
                        typeMapConfigs.size(), entry.getValue().getClass().getSimpleName());
            }

            log.info("Collected {} model mapping configuration(s) from plugin '{}'",
                    allConfigs.size(), pluginId);

            buildMapperTypeConfig(pluginCtx, allConfigs);

            long duration = System.currentTimeMillis() - startTime;
            log.info("Successfully registered model mappings for plugin: {} ({} configs, took {} ms)",
                    pluginId, allConfigs.size(), duration);

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("Failed to register model mappings for plugin: {} (took {} ms)", pluginId, duration, e);
            throw e;
        }
    }

    private synchronized void buildMapperTypeConfig(ApplicationContext applicationContext,
                                                    List<GJPluginTypeMapConfig> configs) {
        long startTime = System.currentTimeMillis();

        log.info("building active ModelMapper with {} total mapping configurations", configs.size());

        try {
            // Try to reuse the main application's ModelMapper so plugins share mappings
            ApplicationContext parentCtx = applicationContext.getParent();
            ModelMapper modelMapper;
            if (parentCtx != null) {
                try {
                    modelMapper = parentCtx.getBean(ModelMapper.class);
                    log.debug("Reusing main application ModelMapper for plugin mappings");
                } catch (NoSuchBeanDefinitionException e) {
                    modelMapper = new GJPluginModelMapper().build();
                    log.debug("No main application ModelMapper found, creating new one");
                }
            } else {
                modelMapper = new GJPluginModelMapper().build();
            }

            int appliedCount = 0;
            for (GJPluginTypeMapConfig config : configs) {
                Class<?> sourceType = config.getSourceType();
                Class<?> destType = config.getDestinationType();
                TypeMap<?, ?> existingTypeMap = modelMapper.getTypeMap(sourceType, destType);
                if (existingTypeMap != null) {
                    log.debug("Merging additional model mapping config for {} -> {}",
                            sourceType.getSimpleName(), destType.getSimpleName());
                    config.getMappingConfigurer().accept(existingTypeMap);
                } else {
                    TypeMap<?, ?> typeMap = modelMapper.createTypeMap(sourceType, destType);
                    log.trace("Registered new model mapping: {} -> {}",
                            sourceType.getSimpleName(), destType.getSimpleName());
                    config.getMappingConfigurer().accept(typeMap);
                }
                appliedCount++;
            }

            // If parent didn't provide a ModelMapper, register in plugin context so plugin beans can inject it
            if (parentCtx == null || !parentCtx.containsBean("modelMapper")) {
                ConfigurableListableBeanFactory beanFactory =
                        ((AnnotationConfigApplicationContext) applicationContext).getBeanFactory();
                if (!beanFactory.containsBean("modelMapper")) {
                    beanFactory.registerSingleton("modelMapper", modelMapper);
                }
            }

            long duration = System.currentTimeMillis() - startTime;
            log.info("built active ModelMapper successfully (applied {} mappings, took {} ms)",
                    appliedCount, duration);
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("Failed to build ModelMapper (took {} ms)", duration, e);
            throw new RuntimeException("ModelMapper build failed", e);
        }
    }
}
