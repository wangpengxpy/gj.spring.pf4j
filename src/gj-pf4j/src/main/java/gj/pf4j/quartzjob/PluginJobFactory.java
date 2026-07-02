/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.quartzjob;

import org.quartz.spi.TriggerFiredBundle;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.quartz.SpringBeanJobFactory;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnClass(SpringBeanJobFactory.class)
public class PluginJobFactory extends SpringBeanJobFactory {

    private final AutowireCapableBeanFactory beanFactory;

    public PluginJobFactory(ApplicationContext applicationContext) {
        this.beanFactory = applicationContext.getAutowireCapableBeanFactory();
    }

    @Override
    protected Object createJobInstance(TriggerFiredBundle bundle) throws Exception {
        Object job = super.createJobInstance(bundle);
        beanFactory.autowireBean(job);
        return job;
    }
}
