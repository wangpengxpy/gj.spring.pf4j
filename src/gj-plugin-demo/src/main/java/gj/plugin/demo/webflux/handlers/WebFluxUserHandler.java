package gj.plugin.demo.webflux.handlers;

import gj.plugin.demo.webflux.dto.WebFluxUserCreateRequest;
import gj.plugin.demo.webflux.dto.WebFluxUserResponse;
import gj.plugin.demo.webflux.service.WebFluxUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

// This handler is only activated in WebFlux mode. The demo host defaults to MVC.
// To test WebFlux: (1) switch the application pom.xml to spring-boot-starter-webflux,
// (2) comment out the gj.plugin.demo.mvc.controllers package in DemoPlugin.
@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
@RequiredArgsConstructor
public class WebFluxUserHandler {

    private final WebFluxUserService webFluxUserService;

    public Mono<ServerResponse> getAll(ServerRequest request) {
        return ServerResponse.ok().body(webFluxUserService.getAll(), WebFluxUserResponse.class);
    }

    public Mono<ServerResponse> getById(ServerRequest request) {
        Long id = Long.parseLong(request.pathVariable("id"));
        return webFluxUserService.getById(id)
                .flatMap(user -> ServerResponse.ok().bodyValue(user))
                .switchIfEmpty(ServerResponse.notFound().build());
    }

    public Mono<ServerResponse> create(ServerRequest request) {
        return request.bodyToMono(WebFluxUserCreateRequest.class)
                .flatMap(req -> webFluxUserService.create(req))
                .flatMap(user -> ServerResponse.ok().bodyValue(user));
    }

    public Mono<ServerResponse> update(ServerRequest request) {
        Long id = Long.parseLong(request.pathVariable("id"));
        return request.bodyToMono(WebFluxUserCreateRequest.class)
                .flatMap(req -> webFluxUserService.update(id, req))
                .flatMap(user -> ServerResponse.ok().bodyValue(user))
                .switchIfEmpty(ServerResponse.notFound().build());
    }

    public Mono<ServerResponse> delete(ServerRequest request) {
        Long id = Long.parseLong(request.pathVariable("id"));
        return webFluxUserService.delete(id).then(ServerResponse.ok().build());
    }

    public Mono<ServerResponse> search(ServerRequest request) {
        return request.queryParam("keyword")
                .map(keyword -> ServerResponse.ok().body(webFluxUserService.search(keyword), WebFluxUserResponse.class))
                .orElseGet(() -> ServerResponse.badRequest().build());
    }

    public Mono<ServerResponse> publicList(ServerRequest request) {
        return ServerResponse.ok().body(webFluxUserService.publicList(), WebFluxUserResponse.class);
    }
}
