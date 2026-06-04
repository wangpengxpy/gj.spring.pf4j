package ${packagePrefix}.${pluginName}.controllers;

import ${packagePrefix}.${pluginName}.response.${pluginName}Response;
import ${packagePrefix}.${pluginName}.service.${pluginName}Service;
import iotcenter.common.OperateResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("${url.manage.prefix}${url.manage.version}/${pluginName}")
public class ${pluginName}Controller {

    private final ${pluginName}Service pluginService;

    public ${pluginName}Controller(${pluginName}Service pluginService) {
        this.pluginService = pluginService;
    }

    /**
     * 获取${pluginName}列表
     *
     * @return OperateResult<List<${pluginName}Response>>
     */
    @GetMapping("/list")
    public OperateResult<List<${pluginName}Response>> getList() {
        return pluginService.getList();
    }
}