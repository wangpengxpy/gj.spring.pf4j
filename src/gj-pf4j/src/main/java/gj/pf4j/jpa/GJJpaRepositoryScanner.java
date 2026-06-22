package gj.pf4j.jpa;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactoryBean;
import org.springframework.data.repository.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

class GJJpaRepositoryScanner {

    private static final Logger log = LoggerFactory.getLogger(GJJpaRepositoryScanner.class);

    List<Class<?>> scan(String basePackage, ClassLoader classLoader) {
        log.debug("Scanning JPA repositories in package: {}", basePackage);

        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AssignableTypeFilter(Repository.class));

        ResourcePatternResolver resolver =
                new PathMatchingResourcePatternResolver(classLoader);
        scanner.setResourceLoader(resolver);

        Set<BeanDefinition> candidates = scanner.findCandidateComponents(basePackage);
        List<Class<?>> result = new ArrayList<>();

        for (BeanDefinition candidate : candidates) {
            try {
                Class<?> clazz = Class.forName(
                        candidate.getBeanClassName(), false, classLoader);
                if (clazz.isInterface()) {
                    result.add(clazz);
                    log.debug("Found JPA repository: {}", clazz.getName());
                }
            } catch (ClassNotFoundException e) {
                log.warn("Failed to load repository candidate: {}", candidate.getBeanClassName(), e);
            }
        }

        log.debug("Found {} JPA repository interfaces in package: {}", result.size(), basePackage);
        return result;
    }

    BeanDefinition createRepositoryBeanDefinition(Class<?> repositoryInterface,
                                                   String entityManagerFactoryBeanName) {
        BeanDefinitionBuilder builder = BeanDefinitionBuilder
                .genericBeanDefinition(JpaRepositoryFactoryBean.class);
        builder.addPropertyValue("repositoryInterface", repositoryInterface);
        builder.addPropertyReference("entityManagerFactory", entityManagerFactoryBeanName);
        builder.setLazyInit(false);
        return builder.getBeanDefinition();
    }
}
