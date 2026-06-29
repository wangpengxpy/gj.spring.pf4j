package gj.plugin.demo.webflux.serviceimpl;

import gj.plugin.demo.entity.WebFluxUserEntity;
import gj.plugin.demo.repository.WebFluxUserRepository;
import gj.plugin.demo.webflux.dto.WebFluxUserCreateRequest;
import gj.plugin.demo.webflux.dto.WebFluxUserResponse;
import gj.plugin.demo.webflux.service.WebFluxUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

// This service is only activated in WebFlux mode. The demo host defaults to MVC.
// To test WebFlux: (1) switch the application pom.xml to spring-boot-starter-webflux,
// (2) comment out the gj.plugin.demo.mvc.controllers package in DemoPlugin.
@Slf4j
@Service
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
@Transactional
@RequiredArgsConstructor
public class WebFluxUserServiceImpl implements WebFluxUserService {

    private final WebFluxUserRepository webFluxUserRepository;
    private final ModelMapper modelMapper;

    @Override
    public Flux<WebFluxUserResponse> getAll() {
        return Mono.fromCallable(webFluxUserRepository::findAll)
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(Flux::fromIterable)
                .map(e -> modelMapper.map(e, WebFluxUserResponse.class));
    }

    @Override
    public Mono<WebFluxUserResponse> getById(Long id) {
        return Mono.fromCallable(() -> webFluxUserRepository.findById(id))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(opt -> opt
                        .map(e -> Mono.just(modelMapper.map(e, WebFluxUserResponse.class)))
                        .orElseGet(Mono::empty));
    }

    @Override
    public Mono<WebFluxUserResponse> create(WebFluxUserCreateRequest request) {
        return Mono.fromCallable(() -> {
            WebFluxUserEntity entity = modelMapper.map(request, WebFluxUserEntity.class);
            return webFluxUserRepository.save(entity);
        })
                .subscribeOn(Schedulers.boundedElastic())
                .map(e -> modelMapper.map(e, WebFluxUserResponse.class));
    }

    @Override
    public Mono<WebFluxUserResponse> update(Long id, WebFluxUserCreateRequest request) {
        return Mono.fromCallable(() -> webFluxUserRepository.findById(id))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(opt -> opt.map(existing -> {
                    existing.setName(request.getName());
                    existing.setEmail(request.getEmail());
                    existing.setDescription(request.getDescription());
                    WebFluxUserEntity saved = webFluxUserRepository.save(existing);
                    return Mono.just(modelMapper.map(saved, WebFluxUserResponse.class));
                }).orElseGet(Mono::empty));
    }

    @Override
    public Mono<Void> delete(Long id) {
        return Mono.fromRunnable(() -> webFluxUserRepository.deleteById(id))
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    @Override
    public Flux<WebFluxUserResponse> search(String keyword) {
        return Mono.fromCallable(() ->
                        webFluxUserRepository.findByNameContainingOrEmailContaining(keyword, keyword))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(Flux::fromIterable)
                .map(e -> modelMapper.map(e, WebFluxUserResponse.class));
    }

    @Override
    public Flux<WebFluxUserResponse> publicList() {
        return Mono.fromCallable(webFluxUserRepository::findAll)
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(Flux::fromIterable)
                .take(10)
                .map(e -> modelMapper.map(e, WebFluxUserResponse.class));
    }
}
