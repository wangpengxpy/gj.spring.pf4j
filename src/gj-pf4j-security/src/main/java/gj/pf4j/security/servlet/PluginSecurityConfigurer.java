/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.security.servlet;

import gj.pf4j.core.PluginAnonymousPathRegistry;
import gj.pf4j.utils.PluginHttpUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.security.config.annotation.SecurityConfigurerAdapter;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.DefaultSecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

/**
 * One-line wiring for all plugin security concerns:
 * <ol>
 *   <li>Six-position composite filters</li>
 *   <li>Plugin authentication filter</li>
 *   <li>Anonymous path permitAll</li>
 * </ol>
 *
 * <p><strong>Host usage:</strong>
 * <pre>{@code
 * @Bean
 * public SecurityFilterChain filterChain(HttpSecurity http,
 *         PluginSecurityConfigurer pluginSecurity) throws Exception {
 *     http.apply(pluginSecurity)
 *         .authorizeHttpRequests(auth -> auth
 *             .requestMatchers("/api/**").authenticated()
 *             .anyRequest().permitAll())
 *         .build();
 * }
 * }</pre>
 */
public class PluginSecurityConfigurer
        extends SecurityConfigurerAdapter<DefaultSecurityFilterChain, HttpSecurity> {

    private final PluginAnonymousPathRegistry anonymousPaths;

    public PluginSecurityConfigurer(PluginAnonymousPathRegistry anonymousPaths) {
        this.anonymousPaths = anonymousPaths;
    }

    @Override
    public void configure(HttpSecurity http) throws Exception {
        ApplicationContext ctx = http.getSharedObject(ApplicationContext.class);

        // 1. Six-position composite filters
        http.addFilterBefore(
                ctx.getBean("pluginFirstFilter", PluginCompositeFilter.class),
                SecurityContextHolderFilter.class);

        http.addFilterAfter(
                ctx.getBean("pluginSessionRestoreFilter", PluginCompositeFilter.class),
                SecurityContextHolderFilter.class);

        http.addFilterAt(
                ctx.getBean("pluginFormLoginFilter", PluginCompositeFilter.class),
                UsernamePasswordAuthenticationFilter.class);

        // 2. Plugin authentication filter
        http.addFilterBefore(
                ctx.getBean(PluginDelegatingAuthFilter.class),
                UsernamePasswordAuthenticationFilter.class);

        http.addFilterAfter(
                ctx.getBean("pluginAnonymousFilter", PluginCompositeFilter.class),
                AnonymousAuthenticationFilter.class);

        http.addFilterBefore(
                ctx.getBean("pluginPreAuthorizeFilter", PluginCompositeFilter.class),
                AuthorizationFilter.class);

        http.addFilterAfter(
                ctx.getBean("pluginLastFilter", PluginCompositeFilter.class),
                AuthorizationFilter.class);

        // 3. Anonymous paths — first matcher, highest priority
        // Always registered: lambda evaluates at request time,
        // so plugins loaded later are covered.
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers(req ->
                        anonymousPaths.isAnonymous(
                                PluginHttpUtils.getPathWithinApplication(req),
                                req.getMethod()))
                .permitAll());
    }
}
