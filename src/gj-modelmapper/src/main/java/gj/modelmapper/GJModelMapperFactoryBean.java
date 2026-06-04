package gj.modelmapper;

import lombok.Setter;
import org.modelmapper.ModelMapper;
import org.modelmapper.TypeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.util.Assert;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.List;

public class GJModelMapperFactoryBean<T> implements FactoryBean<T>, ApplicationContextAware, InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(GJModelMapperFactoryBean.class);

    @Setter
    private Class<T> mapperInterface;

    private ModelMapper modelMapper;
    private ApplicationContext applicationContext;

    public GJModelMapperFactoryBean() {
    }

    public GJModelMapperFactoryBean(Class<T> mapperInterface) {
        Assert.notNull(mapperInterface, "Property 'mapperInterface' is required");
        log.error("Property 'mapperInterface' is required");
        this.mapperInterface = mapperInterface;
    }

    public void processMapperConfigByClass(Class<?> mapperInterface) {
        try {
            Constructor<?> constructor = mapperInterface.getDeclaredConstructor();
            Object instance = constructor.newInstance();
            if (!(instance instanceof GJModelMapperConfig)) {
                log.error("Class [{}] does not implement GJModelMapperConfig interface, cannot process", mapperInterface.getName());
                return;
            }
            GJModelMapperConfig modelMapperConfig = (GJModelMapperConfig) instance;

            List<GJModelMapperTypeMapConfig> A = modelMapperConfig.getTypeMapConfigs();

            modelMapperConfig.getTypeMapConfigs()
                    .stream()
                    .filter(config -> config != null)
                    .forEach(this::processSingleTypeMapConfig);
            log.info("Successfully processed ModelMapper configuration for [{}]", mapperInterface.getName());
        } catch (NoSuchMethodException e) {
            log.error("Class [{}] has no no-arg constructor, cannot instantiate", mapperInterface.getName(), e);
            throw new IllegalArgumentException("No default constructor found for " + mapperInterface.getName(), e);
        } catch (InstantiationException e) {
            log.error("Class [{}] is abstract class/interface, cannot instantiate", mapperInterface.getName(), e);
            throw new IllegalArgumentException("Cannot instantiate abstract class/interface: " + mapperInterface.getName(), e);
        } catch (IllegalAccessException e) {
            log.error("Insufficient access rights to constructor of class [{}]", mapperInterface.getName(), e);
            throw new IllegalArgumentException("No access to constructor of " + mapperInterface.getName(), e);
        } catch (InvocationTargetException e) {
            log.error("Constructor of class [{}] threw an exception during execution", mapperInterface.getName(), e);
            throw new IllegalArgumentException("Constructor of " + mapperInterface.getName() + " threw exception", e);
        }
    }

    private void processSingleTypeMapConfig(GJModelMapperTypeMapConfig config) {
        Class<?> sourceType = config.getSourceType();
        Class<?> destType = config.getDestinationType();
        if (sourceType == null || destType == null) {
            log.error("TypeMap configuration item exception: Source type [{}] or destination type [{}] is null, skip this configuration", sourceType, destType);
            return;
        }
        ModelMapper modelMapper = this.modelMapper;
        TypeMap<?, ?> existingTypeMap = modelMapper.getTypeMap(sourceType, destType);
        if (existingTypeMap != null) {
            log.debug("Merge TypeMap configuration: {} -> {}", sourceType.getSimpleName(), destType.getSimpleName());
            config.getMappingConfigurer().accept(existingTypeMap);
        } else {
            TypeMap<?, ?> newTypeMap = modelMapper.createTypeMap(sourceType, destType);
            log.debug("Register new TypeMap: {} -> {}", sourceType.getSimpleName(), destType.getSimpleName());
            config.getMappingConfigurer().accept(newTypeMap);
        }
    }

    @Override
    public T getObject() {
        throw new UnsupportedOperationException(
                "Conversion from Class<T> to T instance is not supported temporarily"
        );
    }

    @Override
    public Class<T> getObjectType() {
        return this.mapperInterface;
    }

    @Override
    public void afterPropertiesSet() {
        this.modelMapper = this.applicationContext.getBean(ModelMapper.class);
        Assert.notNull(this.modelMapper, "Property 'modelMapper' is required");
        processMapperConfigByClass(this.mapperInterface);
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }
}
