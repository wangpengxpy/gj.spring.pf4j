package gj.demo.config;

import gj.pf4j.anonymous.PluginAnonymousPathRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import reactor.core.publisher.Mono;

@Configuration
@EnableWebFluxSecurity
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
@RequiredArgsConstructor
public class WebFluxSecurityConfig {

    private final PluginAnonymousPathRegistry anonymousPathRegistry;

    @Bean
    public SecurityWebFilterChain filterChain(ServerHttpSecurity http) {
        http.csrf(ServerHttpSecurity.CsrfSpec::disable)
            .authorizeExchange(exchanges -> exchanges
                .pathMatchers("/api/**").access((authentication, context) -> {
                    String path = context.getExchange().getRequest().getURI().getPath();
                    String method = context.getExchange().getRequest().getMethod().name();
                    if (anonymousPathRegistry.isAnonymous(path, method)) {
                        return Mono.just(new AuthorizationDecision(true));
                    }
                    return authentication.map(auth ->
                        new AuthorizationDecision(auth.isAuthenticated()));
                })
                .anyExchange().permitAll()
            )
            .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
            .formLogin(ServerHttpSecurity.FormLoginSpec::disable);
        return http.build();
    }
}
