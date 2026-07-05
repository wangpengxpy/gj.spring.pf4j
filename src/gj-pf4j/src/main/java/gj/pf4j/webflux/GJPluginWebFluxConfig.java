/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.webflux;

import gj.pf4j.anonymous.PluginAnonymousPathRegistrar;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.boot.autoconfigure.web.reactive.WebFluxRegistrations;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.accept.RequestedContentTypeResolverBuilder;
import org.springframework.web.reactive.result.method.annotation.RequestMappingHandlerAdapter;

@Configuration
@ConditionalOnWebApplication(type = Type.REACTIVE)
public class GJPluginWebFluxConfig {

    @Bean("pluginWebFluxRequestMappingHandlerMapping")
    public GJPluginWebFluxRequestMappingHandlerMapping webFluxRequestMappingHandlerMapping(
            PluginAnonymousPathRegistrar anonymousPathRegistrar) {
        var requestedContentTypeResolver = new RequestedContentTypeResolverBuilder().build();
        GJPluginWebFluxRequestMappingHandlerMapping webFluxHandlerMapping =
                new GJPluginWebFluxRequestMappingHandlerMapping();
        webFluxHandlerMapping.setContentTypeResolver(requestedContentTypeResolver);
        webFluxHandlerMapping.setOrder(-1);
        webFluxHandlerMapping.setUseCaseSensitiveMatch(false);
        webFluxHandlerMapping.setAnonymousPathRegistry(anonymousPathRegistrar);
        return webFluxHandlerMapping;
    }

    @Bean
    WebFluxRegistrations webFluxRegistrations() {
        return new WebFluxRegistrations() {
            @Override
            public RequestMappingHandlerAdapter getRequestMappingHandlerAdapter() {
                return new GJPluginWebFluxSecureRequestMappingHandlerAdapter();
            }
        };
    }
}
