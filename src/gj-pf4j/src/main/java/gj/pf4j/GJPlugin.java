package gj.pf4j;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public abstract class GJPlugin {
    /**
     * The context is ready, relevant beans can be obtained for custom logical operations
     */
    protected abstract void afterApplicationContextReady(
            AnnotationConfigApplicationContext context);

    /**
     * Before the context is refreshed, register relevant beans, excluding Spring built-in annotations
     * such as @Component, @Configuration, @Service, @Repository and @Controller etc
     */
    protected abstract AnnotationConfigApplicationContext beforeApplicationContextRefresh(
            AnnotationConfigApplicationContext context);
}
