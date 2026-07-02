package ${packagePrefix}.${pluginName};

import gj.pf4j.GJPlugin;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * 插件入口
 */
public class ${pluginName}Plugin extends GJPlugin {

    @Override
    protected void afterApplicationContextReady(AnnotationConfigApplicationContext context) {

    }

    @Override
    protected AnnotationConfigApplicationContext beforeApplicationContextRefresh(AnnotationConfigApplicationContext context) {
        return context;
    }
}
