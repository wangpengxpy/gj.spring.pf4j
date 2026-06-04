package gj.modelmapper;

import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.scope.ScopedProxyFactoryBean;
import org.springframework.aop.scope.ScopedProxyUtils;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.annotation.ClassPathBeanDefinitionScanner;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.util.ClassUtils;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

public class ClassPathGJModelMapperScanner extends ClassPathBeanDefinitionScanner {

    private static final Logger log = LoggerFactory.getLogger(ClassPathGJModelMapperScanner.class);

    @Setter
    private Class<?> markerInterface;
    @Setter
    private String defaultScope;
    @Setter
    private Class<? extends Annotation> annotationClass;
    @Setter
    private Class<? extends GJModelMapperFactoryBean> mapperFactoryBeanClass;

    public ClassPathGJModelMapperScanner(BeanDefinitionRegistry registry) {
        super(registry, false);
    }

    public void registerFilters() {
        boolean acceptAllInterfaces = true;
        if (this.annotationClass != null) {
            this.addIncludeFilter(new AnnotationTypeFilter(this.annotationClass));
            acceptAllInterfaces = false;
        }
        if (this.markerInterface != null) {
            this.addIncludeFilter(new AssignableTypeFilter(this.markerInterface) {
                protected boolean matchClassName(String className) {
                    return false;
                }
            });
            acceptAllInterfaces = false;
        }
        if (acceptAllInterfaces) {
            this.addIncludeFilter((metadataReader, metadataReaderFactory) -> true);
        }
        this.addExcludeFilter((metadataReader, metadataReaderFactory) -> {
            String className = metadataReader.getClassMetadata().getClassName();
            return className.endsWith("package-info");
        });
    }

    public Set<BeanDefinitionHolder> doScan(String... basePackages) {
        Set<BeanDefinitionHolder> beanDefinitions = super.doScan(basePackages);
        if (beanDefinitions.isEmpty()) {
            log.warn("No model mapper was found in '{}' package. Please check your configuration.", Arrays.toString(basePackages));
        } else {
            this.processBeanDefinitions(beanDefinitions);
        }
        return beanDefinitions;
    }

    private void processBeanDefinitions(Set<BeanDefinitionHolder> beanDefinitions) {
        BeanDefinitionRegistry registry = this.getRegistry();
        for (BeanDefinitionHolder holder : beanDefinitions) {
            AbstractBeanDefinition definition = (AbstractBeanDefinition) holder.getBeanDefinition();
            boolean scopedProxy = false;
            if (ScopedProxyFactoryBean.class.getName().equals(definition.getBeanClassName())) {
                definition = (AbstractBeanDefinition) Optional.ofNullable(((RootBeanDefinition) definition).getDecoratedDefinition()).map(BeanDefinitionHolder::getBeanDefinition).orElseThrow(() -> new IllegalStateException("The target bean definition of scoped proxy bean not found. Root bean definition[" + holder + "]"));
                scopedProxy = true;
            }
            String beanClassName = definition.getBeanClassName();
            definition.getConstructorArgumentValues().addGenericArgumentValue(beanClassName);
            try {
                Class<?> beanClass = ClassUtils.forName(beanClassName, ClassLoader.getSystemClassLoader());
                definition.setAttribute("factoryBeanObjectType", beanClass);
                definition.getPropertyValues().add("mapperInterface", beanClass);
            } catch (ClassNotFoundException ignored) {
            }
            definition.setBeanClass(this.mapperFactoryBeanClass);
            if (!scopedProxy) {
                if ("singleton".equals(definition.getScope()) && this.defaultScope != null) {
                    definition.setScope(this.defaultScope);
                }
                if (!definition.isSingleton()) {
                    BeanDefinitionHolder proxyHolder = ScopedProxyUtils.createScopedProxy(holder, registry, true);
                    if (registry.containsBeanDefinition(proxyHolder.getBeanName())) {
                        registry.removeBeanDefinition(proxyHolder.getBeanName());
                    }
                    registry.registerBeanDefinition(proxyHolder.getBeanName(), proxyHolder.getBeanDefinition());
                }
            }
        }
    }

    public void setMapperFactoryBeanClass(Class<? extends GJModelMapperFactoryBean> mapperFactoryBeanClass) {
        this.mapperFactoryBeanClass = mapperFactoryBeanClass == null ? GJModelMapperFactoryBean.class : mapperFactoryBeanClass;
    }

    protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
        AnnotationMetadata metadata = beanDefinition.getMetadata();
        boolean isNormalClass = !metadata.isInterface()
                && !metadata.isAnnotation()
                && !metadata.isAbstract();
        return isNormalClass && metadata.isIndependent();
    }

    protected boolean checkCandidate(String beanName, BeanDefinition beanDefinition) {
        if (super.checkCandidate(beanName, beanDefinition)) {
            return true;
        } else {
            log.error("Skipping ModelMapperFactoryBean with name '{}' and '{}' mapperInterface. Bean already defined with the same name!", beanName, beanDefinition.getBeanClassName());
            return false;
        }
    }
}
