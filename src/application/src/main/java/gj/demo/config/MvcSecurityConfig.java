package gj.demo.config;

import gj.pf4j.core.PluginAnonymousPathRegistry;
import gj.pf4j.security.servlet.PluginSecurityConfigurer;
import gj.pf4j.utils.PluginHttpUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@RequiredArgsConstructor
public class MvcSecurityConfig {

    private final PluginSecurityConfigurer pluginSecurityConfigurer;
    private final PluginAnonymousPathRegistry anonymousPaths;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.with(pluginSecurityConfigurer, Customizer.withDefaults())
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(req -> anonymousPaths.isAnonymous(
                    PluginHttpUtils.getPathWithinApplication(req),
                    req.getMethod())).permitAll()
                .requestMatchers("/api/**").authenticated()
                .anyRequest().permitAll()
            )
            .httpBasic(httpBasic -> {});
        return http.build();
    }
}
