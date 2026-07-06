/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.security;

import gj.pf4j.GJPluginConfig;
import gj.pf4j.core.PluginAnonymousPathRegistry;
import gj.pf4j.GJPluginAuthRegistry;
import gj.pf4j.GJPluginFilterPosition;
import gj.pf4j.GJPluginFilterRegistry;
import gj.pf4j.security.reactive.PluginCompositeWebFilter;
import gj.pf4j.security.reactive.PluginDelegatingAuthWebFilter;
import gj.pf4j.security.servlet.PluginCompositeFilter;
import gj.pf4j.security.servlet.PluginDelegatingAuthFilter;
import gj.pf4j.security.servlet.PluginSecurityConfigurer;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Auto-configuration for gj-pf4j-security.
 * Activated automatically by Spring Boot when the module is on the classpath.
 * <p>
 * All beans are {@code @ConditionalOnMissingBean} — host application can
 * override any of them by defining its own.
 */
@Configuration
@AutoConfigureAfter(GJPluginConfig.class)
@EnableConfigurationProperties(PluginFilterConfigProperties.class)
public class GJSecurityAutoConfiguration {

    // ──── Core beans ──────────────────────────────────────────────

    @Bean
    @ConditionalOnMissingBean(GJPluginAuthRegistry.class)
    public GJPluginAuthRegistry pluginAuthRegistry() {
        return new DefaultPluginAuthRegistry();
    }

    @Bean
    @ConditionalOnMissingBean(PluginAuthenticatedPathRegistry.class)
    public PluginAuthenticatedPathRegistry pluginAuthenticatedPathRegistry() {
        return new DefaultPluginAuthenticatedPathRegistry();
    }

    @Bean
    @ConditionalOnMissingBean(AuthenticationStrategy.class)
    public AuthenticationStrategy authenticationStrategy() {
        return new AtLeastOneSuccessfulStrategy();
    }

    @Bean
    @ConditionalOnMissingBean(GJPluginFilterRegistry.class)
    public GJPluginFilterRegistry pluginFilterRegistry() {
        return new GJPluginFilterRegistry();
    }

    // ──── MVC Filter ─────────────────────────────────────────────

    @Bean
    @ConditionalOnMissingBean(PluginDelegatingAuthFilter.class)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    public PluginDelegatingAuthFilter pluginDelegatingAuthFilter(
            GJPluginAuthRegistry registry,
            PluginAnonymousPathRegistry anonymousPaths,
            PluginAuthenticatedPathRegistry authenticatedPaths,
            AuthenticationStrategy strategy,
            ApplicationEventPublisher eventPublisher) {
        return new PluginDelegatingAuthFilter(registry, anonymousPaths,
                authenticatedPaths, strategy, eventPublisher);
    }

    // ──── MVC Composite Filters (6 positions) ─────────────────────

    @Bean("pluginFirstFilter")
    @ConditionalOnMissingBean(name = "pluginFirstFilter")
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    public PluginCompositeFilter pluginFirstFilter(GJPluginFilterRegistry registry) {
        return new PluginCompositeFilter(registry, GJPluginFilterPosition.FIRST);
    }

    @Bean("pluginSessionRestoreFilter")
    @ConditionalOnMissingBean(name = "pluginSessionRestoreFilter")
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    public PluginCompositeFilter pluginSessionRestoreFilter(GJPluginFilterRegistry registry) {
        return new PluginCompositeFilter(registry, GJPluginFilterPosition.SESSION_RESTORE);
    }

    @Bean("pluginFormLoginFilter")
    @ConditionalOnMissingBean(name = "pluginFormLoginFilter")
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    public PluginCompositeFilter pluginFormLoginFilter(GJPluginFilterRegistry registry) {
        return new PluginCompositeFilter(registry, GJPluginFilterPosition.FORM_LOGIN);
    }

    @Bean("pluginAnonymousFilter")
    @ConditionalOnMissingBean(name = "pluginAnonymousFilter")
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    public PluginCompositeFilter pluginAnonymousFilter(GJPluginFilterRegistry registry) {
        return new PluginCompositeFilter(registry, GJPluginFilterPosition.ANONYMOUS);
    }

    @Bean("pluginPreAuthorizeFilter")
    @ConditionalOnMissingBean(name = "pluginPreAuthorizeFilter")
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    public PluginCompositeFilter pluginPreAuthorizeFilter(GJPluginFilterRegistry registry) {
        return new PluginCompositeFilter(registry, GJPluginFilterPosition.PRE_AUTHORIZE);
    }

    @Bean("pluginLastFilter")
    @ConditionalOnMissingBean(name = "pluginLastFilter")
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    public PluginCompositeFilter pluginLastFilter(GJPluginFilterRegistry registry) {
        return new PluginCompositeFilter(registry, GJPluginFilterPosition.LAST);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    public PluginSecurityConfigurer pluginSecurityConfigurer(
            PluginAnonymousPathRegistry anonymousPaths) {
        return new PluginSecurityConfigurer(anonymousPaths);
    }

    // ──── WebFlux Filter ──────────────────────────────────────────

    @Bean
    @ConditionalOnMissingBean(PluginDelegatingAuthWebFilter.class)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
    public PluginDelegatingAuthWebFilter pluginDelegatingAuthWebFilter(
            GJPluginAuthRegistry registry,
            PluginAnonymousPathRegistry anonymousPaths,
            PluginAuthenticatedPathRegistry authenticatedPaths,
            AuthenticationStrategy strategy,
            ApplicationEventPublisher eventPublisher) {
        return new PluginDelegatingAuthWebFilter(registry, anonymousPaths,
                authenticatedPaths, strategy, eventPublisher);
    }

    // ──── WebFlux Composite Filters (6 positions) ─────────────────

    @Bean("pluginFirstWebFilter")
    @ConditionalOnMissingBean(name = "pluginFirstWebFilter")
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
    public PluginCompositeWebFilter pluginFirstWebFilter(GJPluginFilterRegistry registry) {
        return new PluginCompositeWebFilter(registry, GJPluginFilterPosition.FIRST,
                org.springframework.core.Ordered.HIGHEST_PRECEDENCE + 5);
    }

    @Bean("pluginSessionRestoreWebFilter")
    @ConditionalOnMissingBean(name = "pluginSessionRestoreWebFilter")
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
    public PluginCompositeWebFilter pluginSessionRestoreWebFilter(GJPluginFilterRegistry registry) {
        return new PluginCompositeWebFilter(registry, GJPluginFilterPosition.SESSION_RESTORE,
                org.springframework.core.Ordered.HIGHEST_PRECEDENCE + 15);
    }

    @Bean("pluginFormLoginWebFilter")
    @ConditionalOnMissingBean(name = "pluginFormLoginWebFilter")
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
    public PluginCompositeWebFilter pluginFormLoginWebFilter(GJPluginFilterRegistry registry) {
        return new PluginCompositeWebFilter(registry, GJPluginFilterPosition.FORM_LOGIN,
                org.springframework.core.Ordered.HIGHEST_PRECEDENCE + 25);
    }

    @Bean("pluginAnonymousWebFilter")
    @ConditionalOnMissingBean(name = "pluginAnonymousWebFilter")
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
    public PluginCompositeWebFilter pluginAnonymousWebFilter(GJPluginFilterRegistry registry) {
        return new PluginCompositeWebFilter(registry, GJPluginFilterPosition.ANONYMOUS,
                org.springframework.core.Ordered.HIGHEST_PRECEDENCE + 35);
    }

    @Bean("pluginPreAuthorizeWebFilter")
    @ConditionalOnMissingBean(name = "pluginPreAuthorizeWebFilter")
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
    public PluginCompositeWebFilter pluginPreAuthorizeWebFilter(GJPluginFilterRegistry registry) {
        return new PluginCompositeWebFilter(registry, GJPluginFilterPosition.PRE_AUTHORIZE,
                org.springframework.core.Ordered.HIGHEST_PRECEDENCE + 45);
    }

    @Bean("pluginLastWebFilter")
    @ConditionalOnMissingBean(name = "pluginLastWebFilter")
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
    public PluginCompositeWebFilter pluginLastWebFilter(GJPluginFilterRegistry registry) {
        return new PluginCompositeWebFilter(registry, GJPluginFilterPosition.LAST,
                org.springframework.core.Ordered.HIGHEST_PRECEDENCE + 55);
    }
}
