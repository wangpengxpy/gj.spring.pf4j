package gj.pf4j.examples.controller;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/example")
public class ExampleController {

    @GetMapping("/hello")
    public String hello() {
        return "Hello from gj.spring.pf4j plugin!";
    }

    @GetMapping("/hello/{name}")
    public String helloName(@PathVariable String name) {
        return "Hello, " + name + "!";
    }
}
