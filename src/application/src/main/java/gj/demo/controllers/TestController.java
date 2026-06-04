package gj.demo.controllers;

import gj.data.model.Test;
import gj.data.service.TestService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("api/test")
public class TestController {
    private final TestService testService;

    @GetMapping("getAll")
    public List<Test> getAll() {
        return testService.getAllTests();
    }

    @GetMapping("/{id}")
    public Test get(@PathVariable Long id) {
        return testService.getTestById(id);
    }

    @PostMapping("save")
    public void save(@RequestBody Test test) {
        testService.saveTest(test);
    }

    @PutMapping("update")
    public void update(@RequestBody Test test) {
        testService.updateTest(test);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        testService.deleteTest(id);
    }
}