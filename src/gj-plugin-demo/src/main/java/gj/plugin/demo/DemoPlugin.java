package gj.plugin.demo;

import gj.pf4j.GJPlugin;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class DemoPlugin extends GJPlugin {
    @Override
    protected void afterApplicationContextReady(AnnotationConfigApplicationContext context) {
    }

    @Override
    protected AnnotationConfigApplicationContext beforeApplicationContextRefresh(AnnotationConfigApplicationContext context) {
        return null;
    }
}