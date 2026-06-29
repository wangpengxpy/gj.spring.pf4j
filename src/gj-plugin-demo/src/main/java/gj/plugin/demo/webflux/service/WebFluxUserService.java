package gj.plugin.demo.webflux.service;

import gj.plugin.demo.webflux.dto.WebFluxUserCreateRequest;
import gj.plugin.demo.webflux.dto.WebFluxUserResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface WebFluxUserService {

    Flux<WebFluxUserResponse> getAll();

    Mono<WebFluxUserResponse> getById(Long id);

    Mono<WebFluxUserResponse> create(WebFluxUserCreateRequest request);

    Mono<WebFluxUserResponse> update(Long id, WebFluxUserCreateRequest request);

    Mono<Void> delete(Long id);

    Flux<WebFluxUserResponse> search(String keyword);

    Flux<WebFluxUserResponse> publicList();
}
