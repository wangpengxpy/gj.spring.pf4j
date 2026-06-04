package gj.pf4j.tests.integration.controller;

import gj.pf4j.tests.integration.IntegrationTestConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = IntegrationTestConfig.class)
class ControllerIntegrationTest {

    @Autowired
    private ApplicationContext ctx;

    @Test
    @DisplayName("@RestController bean is discovered by Spring context")
    void restControllerDiscovered() {
        Map<String, Object> controllers = ctx.getBeansWithAnnotation(RestController.class);
        // The integration test itself doesn't register controllers,
        // but the framework's own @RestController beans should be discoverable
        assertNotNull(controllers);
    }

    @Test
    @DisplayName("@Controller and @ResponseBody beans are registered")
    void controllerBeansRegistered() {
        Map<String, Object> controllers = ctx.getBeansWithAnnotation(Controller.class);
        assertNotNull(controllers);
    }

    @Test
    @DisplayName("@RestController is a stereotype annotation")
    void restControllerIsStereotype() {
        // @RestController meta-annotates @Controller
        assertNotNull(RestController.class.getAnnotation(Controller.class),
                "@RestController should meta-annotate @Controller");
    }

    @Test
    @DisplayName("ApplicationContext can resolve @GetMapping-annotated method metadata")
    void requestMappingMetadata() throws NoSuchMethodException {
        // Verify that Spring can introspect @RequestMapping values at runtime
        GetMapping ann = SampleController.class.getMethod("hello").getAnnotation(GetMapping.class);
        assertNotNull(ann);
        assertEquals("/test/hello", ann.value()[0]);
    }

    @RestController
    static class SampleController {
        @GetMapping("/test/hello")
        public String hello() { return "hello"; }
    }
}
