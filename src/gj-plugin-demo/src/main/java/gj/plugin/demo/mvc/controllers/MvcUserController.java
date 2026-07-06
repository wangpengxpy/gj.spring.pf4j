package gj.plugin.demo.mvc.controllers;

import gj.pf4j.core.AllowAnonymous;
import gj.pf4j.core.PluginAuthenticated;
import gj.plugin.demo.mvc.dto.MvcUserCreateRequest;
import gj.plugin.demo.mvc.dto.MvcUserResponse;
import gj.plugin.demo.mvc.service.MvcUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@PluginAuthenticated
@Tag(name = "MVC用户管理", description = "OR 鉴权示例：Session 用户直接放行，外部调用需 X-Demo-Api-Key")
@RestController
@RequestMapping("/api/v1/mvc/users")
@RequiredArgsConstructor
public class MvcUserController {

    private final MvcUserService mvcUserService;

    @Operation(summary = "查询全部用户")
    @GetMapping("/list")
    public List<MvcUserResponse> getList() {
        return mvcUserService.getList();
    }

    @Operation(summary = "根据ID查询用户")
    @GetMapping("/{id}")
    public MvcUserResponse getById(
            @Parameter(description = "用户ID") @PathVariable Integer id) {
        return mvcUserService.getById(id);
    }

    @Operation(summary = "创建用户")
    @PostMapping("/create")
    public boolean create(
            @Parameter(description = "创建请求") @RequestBody MvcUserCreateRequest request) {
        return mvcUserService.create(request);
    }

    @Operation(summary = "更新用户")
    @PutMapping("/{id}")
    public boolean update(
            @Parameter(description = "用户ID") @PathVariable Integer id,
            @Parameter(description = "更新请求") @RequestBody MvcUserCreateRequest request) {
        return mvcUserService.update(id, request);
    }

    @Operation(summary = "删除用户")
    @DeleteMapping("/{id}")
    public boolean delete(
            @Parameter(description = "用户ID") @PathVariable Integer id) {
        return mvcUserService.delete(id);
    }

    @AllowAnonymous(reason = "搜索接口对未登录用户开放")
    @Operation(summary = "搜索用户（匿名）")
    @GetMapping("/search")
    public List<MvcUserResponse> search(
            @Parameter(description = "搜索关键词") @RequestParam String keyword) {
        return mvcUserService.search(keyword);
    }
}
