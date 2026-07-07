/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.security.servlet;

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
 *   <li>Anonymous path permitAll (host must wire — see PluginAnonymousPathRegistry)</li>
 * </ol>
 *
 * <p><strong>Host usage:</strong>
 * <pre>{@code
 * @Bean
 * public SecurityFilterChain filterChain(HttpSecurity http,
 *         PluginSecurityConfigurer pluginSecurity) throws Exception {
 *     http.with(pluginSecurity, Customizer.withDefaults())
 *         .authorizeHttpRequests(auth -> auth
 *             .requestMatchers("/api/**").authenticated()
 *             .anyRequest().permitAll())
 *         .build();
 * }
 * }</pre>
 */
public class PluginSecurityConfigurer
        extends SecurityConfigurerAdapter<DefaultSecurityFilterChain, HttpSecurity> {

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

        // Note: Anonymous path permitAll rules must be wired by the host
        // as the first matcher in its own authorizeHttpRequests chain.
        // See PluginAnonymousPathRegistry Javadoc for the correct pattern.
    }
}
