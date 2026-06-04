package ${packagePrefix}.${pluginName};

import iotcenter.pf4j.IoTPlugin;
import org.pf4j.PluginWrapper;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * 插件入口
 */
public class ${pluginName}Plugin extends IoTPlugin {

    public ${pluginName}Plugin(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Override
    public void start() {
        super.start();
    }

    @Override
    public void stop() {
        super.stop();
    }

    @Override
    protected void afterApplicationContextReady(AnnotationConfigApplicationContext context) {

    }

    @Override
    protected AnnotationConfigApplicationContext beforeApplicationContextRefresh(AnnotationConfigApplicationContext context) {
        return context;
    }
}
