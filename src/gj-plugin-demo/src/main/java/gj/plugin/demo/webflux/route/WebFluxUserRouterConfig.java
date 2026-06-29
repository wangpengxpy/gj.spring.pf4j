package gj.plugin.demo.webflux.route;

import gj.pf4j.webflux.GJPluginWebFluxRouterFunctionRegistry;
import gj.plugin.demo.webflux.handlers.WebFluxUserHandler;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.RequestPredicates;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

import java.util.List;

import static gj.pf4j.webflux.GJRouterFunctions.route;
import static gj.pf4j.webflux.GJRouterFunctions.wrap;

// This router config is only activated in WebFlux mode. The demo host defaults to MVC.
// To test WebFlux: (1) switch the application pom.xml to spring-boot-starter-webflux,
// (2) comment out the gj.plugin.demo.mvc.controllers package in DemoPlugin.
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
@RequiredArgsConstructor
public class WebFluxUserRouterConfig {

    private final GJPluginWebFluxRouterFunctionRegistry registry;
    private final WebFluxUserHandler handler;

    @PostConstruct
    public void registerRoutes() {
        RouterFunction<ServerResponse> simpleRoutes = route()
                .GET("/api/v1/webflux/users/search", handler::search, "用户搜索对未登录用户开放")
                .GET("/api/v1/webflux/users/public", handler::publicList, "公开用户列表，无需登录")
                .GET("/api/v1/webflux/users", handler::getAll)
                .GET("/api/v1/webflux/users/{id}", handler::getById)
                .POST("/api/v1/webflux/users", handler::create)
                .PUT("/api/v1/webflux/users/{id}", handler::update)
                .DELETE("/api/v1/webflux/users/{id}", handler::delete)
                .build();

        RouterFunction<ServerResponse> advancedRoutes = wrap(
                RouterFunctions.route()
                        .nest(RequestPredicates.path("/api/v2/webflux"), v2 -> v2
                                .GET("/users", RequestPredicates.accept(MediaType.APPLICATION_JSON), handler::getAll)
                                .POST("/users", RequestPredicates.contentType(MediaType.APPLICATION_JSON), handler::create)
                        )
                        .build())
                .anonymous("/api/v2/webflux/users", "GET", "嵌套路由中的用户列表查询")
                .anonymous("/api/v2/webflux/users", "POST", "嵌套路由中的用户创建接口")
                .build();

        registry.register(List.of(simpleRoutes, advancedRoutes));
    }
}
