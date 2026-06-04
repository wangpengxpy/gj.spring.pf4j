package gj.pf4j.tests.integration;

import gj.pf4j.eventbus.GJPluginLocalEventBus;
import gj.pf4j.modelmapper.GJPluginModelMapper;
import gj.pf4j.modelmapper.GJPluginModelMapperRegistry;
import gj.pf4j.quartzjob.GJQuartzConfig;
import org.modelmapper.ModelMapper;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@EnableAutoConfiguration
@Import(GJQuartzConfig.class)
public class IntegrationTestConfig {

    @Bean
    public GJPluginLocalEventBus eventBus() {
        return new GJPluginLocalEventBus();
    }

    @Bean
    public ModelMapper modelMapper() {
        return new GJPluginModelMapper().build();
    }

    @Bean
    public GJPluginModelMapperRegistry modelMapperRegistry() {
        return new GJPluginModelMapperRegistry();
    }
}
