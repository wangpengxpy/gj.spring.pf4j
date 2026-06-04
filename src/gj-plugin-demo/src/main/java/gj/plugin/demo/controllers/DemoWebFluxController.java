package gj.plugin.demo.controllers;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
public class DemoWebFluxController {

    @GetMapping("/plugin/demo/hello")
    public Mono<String> sayHello() {
        return Mono.just("Hello from PF4J WebFlux Plugin!");
    }

    @GetMapping("/plugin/demo/echo/{message}")
    public Mono<String> echo(@PathVariable String message) {
        return Mono.just("Plugin echo: " + message);
    }
}