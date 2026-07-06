package gj.plugin.demo.mvc.controllers;

import gj.pf4j.core.AllowAnonymous;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

@AllowAnonymous(reason = "公开查询接口，无需登录认证")
@Tag(name = "MVC公开接口", description = "无需认证的公开查询")
@RestController
@RequestMapping("/api/v1/mvc/public")
public class MvcPublicController {

    @Operation(summary = "服务信息")
    @GetMapping("/info")
    public Map<String, Object> info() {
        return Map.of(
                "service", "gj-plugin-demo MVC",
                "time", LocalDateTime.now().toString(),
                "status", "running"
        );
    }

    @Operation(summary = "健康检查")
    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }
}
